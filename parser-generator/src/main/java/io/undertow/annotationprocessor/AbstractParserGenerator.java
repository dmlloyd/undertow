/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.annotationprocessor;

import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.jboss.classfilewriter.AccessFlag;
import org.jboss.classfilewriter.ClassFile;
import org.jboss.classfilewriter.ClassMethod;
import org.jboss.classfilewriter.code.BranchEnd;
import org.jboss.classfilewriter.code.CodeAttribute;
import org.jboss.classfilewriter.code.CodeLocation;
import org.jboss.classfilewriter.code.TableSwitchBuilder;
import org.jboss.classfilewriter.util.DescriptorUtils;

/**
 * @author Stuart Douglas
 */
public abstract class AbstractParserGenerator {

    //class names
    protected final String parseStateClass;
    protected String resultClass;
    protected final String constructorDescriptor;

    private final String parseStateDescriptor;
    private final String httpExchangeDescriptor;

    public static final String HTTP_STRING_CLASS = "io.undertow.util.HttpString";
    public static final String HTTP_STRING_DESCRIPTOR = DescriptorUtils.makeDescriptor(HTTP_STRING_CLASS);


    //state machine states (valid on method entry/exit)
    // header name state
    public static final int HEADER_NAME = 0;
    // generic header value state
    public static final int HEADER_VALUE = 1;
    // generic header value state
    public static final int SINGLETON_HEADER_VALUE = 2;
    // version string (third stage on requests, first on responses)
    public static final int VERSION = 2;
    // verb state (requests only, first stage)
    public static final int VERB = 3;
    // URI state (requests only)
    public static final int URI = 4;
    // status code (responses only)
    public static final int RESPONSE_CODE = 5;
    // response description (responses only)
    public static final int RESPONSE_DESCRIPTION = 6;
    // special header value states start here including:
    // - preset values
    // - header values with methods (string or integral)
    public static final int SPECIAL_HEADERS = 7;

    private static final int CONSTRUCTOR_HTTP_STRING_MAP_VAR = 1;

    protected static final int BYTE_BUFFER_VAR = 1;
    protected static final int PARSE_STATE_VAR = 2;
    protected static final int HTTP_RESULT = 3;
    protected static final int CURRENT_STATE_VAR = 4;
    protected static final int STATE_POS_VAR = 5;
    protected static final int STATE_CURRENT_VAR = 6;
    protected static final int STATE_STRING_BUILDER_VAR = 7;
    protected static final int STATE_CURRENT_BYTES_VAR = 8;

    public static final String HANDLE_HTTP_VERB = "handleHttpVerb";
    public static final String HANDLE_PATH = "handlePath";
    public static final String HANDLE_HTTP_VERSION = "handleHttpVersion";
    public static final String HANDLE_AFTER_VERSION = "handleAfterVersion";
    public static final String HANDLE_HEADER = "handleHeader";
    public static final String HANDLE_HEADER_VALUE = "handleHeaderValue";
    public static final String CLASS_NAME_SUFFIX = "$$generated";

    public static final String HM_GENERIC_HEADER_VALUE;
    public static final String HM_GENERIC_HEADER_VALUE_CSV;
    public static final String HM_IS_HEADER;
    public static final String HM_IS_LWS;
    public static final String HM_IS_HEADER_VAL;

    public static final int CLASS_SPACE = 0;
    public static final int CLASS_SEP = 1;
    public static final int CLASS_TOK = 2;
    public static final int CLASS_CTL = 3;

    public static final int CONST_OK = 0;
    public static final int CONST_UNDERFLOW = 1;
    public static final int CONST_BAD = 2;

    static {
        int c = 0;

        final String fmt = "$g%03x";
        HM_GENERIC_HEADER_VALUE = String.format(fmt, c++);
        HM_GENERIC_HEADER_VALUE_CSV = String.format(fmt, c++);
        HM_IS_HEADER = String.format(fmt, c++);
        HM_IS_LWS = String.format(fmt, c++);
        HM_IS_HEADER_VAL = String.format(fmt, c++);
    }

    public AbstractParserGenerator(final String parseStateClass, final String resultClass, final String constructorDescriptor) {
        this.parseStateClass = parseStateClass;
        this.resultClass = resultClass;
        parseStateDescriptor = DescriptorUtils.makeDescriptor(parseStateClass);
        httpExchangeDescriptor = DescriptorUtils.makeDescriptor(resultClass);
        this.constructorDescriptor = constructorDescriptor;
    }

    public byte[] createTokenizer(final String existingClassName, final String[] httpVerbs, String[] httpVersions, HttpHeaderConfig[] headers) {
        final String className = existingClassName + CLASS_NAME_SUFFIX;
        final ClassFile file = new ClassFile(className, existingClassName);


        final ClassMethod ctor = file.addMethod(AccessFlag.PUBLIC, "<init>", "V", DescriptorUtils.parameterDescriptors(constructorDescriptor));
        ctor.getCodeAttribute().aload(0);
        ctor.getCodeAttribute().loadMethodParameters();
        ctor.getCodeAttribute().invokespecial(existingClassName, "<init>", constructorDescriptor);
        ctor.getCodeAttribute().returnInstruction();


        final ClassMethod sctor = file.addMethod(AccessFlag.PUBLIC | AccessFlag.STATIC, "<clinit>", "V");
        final AtomicInteger fieldCounter = new AtomicInteger(1);
        sctor.getCodeAttribute().invokestatic(existingClassName, "httpStrings", "()" + DescriptorUtils.makeDescriptor(Map.class));
        sctor.getCodeAttribute().astore(CONSTRUCTOR_HTTP_STRING_MAP_VAR);

        createStateMachines(httpVerbs, httpVersions, headers, className, file, sctor, fieldCounter);

        sctor.getCodeAttribute().returnInstruction();
        return file.toBytecode();
    }

    protected void addGenericHelpers(ClassFile classFile, String exchangeType, String headerMethod) {
        {
            // HEADER is any token char
            // "any CHAR except CTLs or separators"
            // CHAR := 0..127
            // CTLs := 0..31
            // TEXT := 9|32..126|128..255 (i.e. -128..-1)
            // separators := ()<>@,;:\"/[]?={}
            // thus HEADER :=  ! #$%&'  *+ -./
            //                0123456789
            //                 ABCDEFGHIJKLMNO
            //                PQRSTUVWXYZ   ^_
            //                `abcdefghijklmno
            //                pqrstuvwxyz | ~
            //
            // LWS is supposed to include CRLF but we detect that separately
            //
            // HEADER_VALUE is any token OR quoted-string OR separators in any sequence

            final ClassMethod method = classFile.addMethod(AccessFlag.PRIVATE | AccessFlag.STATIC, HM_IS_HEADER, "Z", "B");
            final CodeAttribute c = method.getCodeAttribute();
            c.iload(0);
            c.iconst('z');
            final BranchEnd weird1 = c.ifIcmpgt();
            c.iconst('^');
            final BranchEnd ok = c.ifIcmpge();
            // assert: x <= ^
            c.iconst('A');
            final BranchEnd weird2 = c.ifIcmplt();
            // A <= x <= ^, just exclude [\]
            c.iconst('Z');
            final BranchEnd bad = c.ifIcmpgt();
            c.branchEnd(ok);
            final CodeLocation okBack = c.mark();
            c.iconst(1);
            c.returnInstruction();
            c.branchEnd(weird1);
            // x > 'z'
            c.iconst('|');
            c.ifIcmpeq(okBack);
            c.iconst('~');
            c.ifIcmpeq(okBack);
            c.branchEnd(bad);
            final CodeLocation badBack = c.mark();
            c.iconst(0);
            c.returnInstruction();
            c.branchEnd(weird2);
            // x < A
            c.iconst('9');
            c.ifIcmpgt(badBack);
            c.iconst('-');
            c.ifIcmpge(okBack);
            // only real oddballs remain
            // x < '-'
            c.iconst('!');
            c.ifIcmplt(badBack);
            // '!' <= x < '-'
            c.iconst('"');
            c.ifIcmpeq(badBack);
            c.iconst('(');
            c.ifIcmpeq(badBack);
            c.iconst(')');
            c.ifIcmpeq(badBack);
            c.iconst(',');
            c.ifIcmpeq(badBack);
            c.gotoInstruction(okBack);
        }
        {
            // we don't include CRLF in our LWS production
            final ClassMethod method = classFile.addMethod(AccessFlag.PRIVATE | AccessFlag.STATIC, HM_IS_LWS, "Z", "B");
            final CodeAttribute c = method.getCodeAttribute();
            c.iload(0);
            c.iconst(' ');
            final BranchEnd ok = c.ifIcmpeq();
            c.iload(0);
            c.iconst('\t');
            final BranchEnd ok2 = c.ifIcmpeq();
            c.iconst(0);
            c.returnInstruction();
            c.branchEnd(ok);
            c.branchEnd(ok2);
            c.iconst(1);
            c.returnInstruction();
        }
        {
            // all TEXT
            final ClassMethod method = classFile.addMethod(AccessFlag.PRIVATE | AccessFlag.STATIC, HM_IS_HEADER_VAL, "Z", "B");
            final CodeAttribute c = method.getCodeAttribute();
            c.iload(0);
            c.iconst(9); // HT
            final BranchEnd ok = c.ifIcmpeq();
            c.iload(0);
            c.iconst(0);
            final BranchEnd ok2 = c.ifIcmplt(); // 128-255
            c.iload(0);
            c.iconst(' ');
            final BranchEnd bad = c.ifIcmplt();
            c.iload(0);
            c.iconst(127);
            final BranchEnd ok3 = c.ifIcmpne();
            c.branchEnd(bad);
            c.iconst(0);
            c.returnInstruction();
            c.branchEnd(ok);
            c.branchEnd(ok2);
            c.branchEnd(ok3);
            c.iconst(1);
            c.returnInstruction();
        }
        {
            final ClassMethod method = classFile.addMethod(AccessFlag.PRIVATE | AccessFlag.STATIC, HM_GENERIC_HEADER_VALUE, "int", "io.undertow.util.HttpString", "javax.nio.ByteBuffer");
            int var = 0;
            final CodeAttribute c = method.getCodeAttribute();
            final int headerNameVar = var ++; // parameter
            final int bufVar = var ++; // parameter
            final int limVar = var ++;
            final int posVar = var ++;
            final int startVar = var ++;
            final int byteVar = var ++;
            c.aload(bufVar);
            c.invokevirtual(ByteBuffer.class.getName(), "limit", "()I");
            c.istore(limVar);
            c.aload(bufVar);
            c.invokevirtual(ByteBuffer.class.getName(), "position", "()I");
            c.istore(posVar);

            // state: start
            final CodeLocation start = c.mark();
            c.iload(posVar);
            c.iload(limVar);
            final BranchEnd uf1 = c.ifIcmpeq();
            c.aload(bufVar);
            c.iload(posVar);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "(I)B");
            c.istore(byteVar);
            c.iinc(posVar, 1);
            c.iload(posVar);
            c.istore(startVar);

            c.iload(byteVar);
            c.invokestatic(classFile.getName(), HM_IS_LWS, "(B)Z");
            c.ifEq(start);
            c.iload(byteVar);
            c.iconst(13);
            final BranchEnd state1be = c.ifIcmpeq();
            c.iload(byteVar);
            c.iconst('"');
            final BranchEnd state7be1 = c.ifIcmpeq();
            c.iload(byteVar);
            c.invokestatic(classFile.getName(), HM_IS_HEADER_VAL, "(B)Z");
            final BranchEnd state3be = c.ifeq();
            final BranchEnd err1 = c.gotoInstruction();

            // state 1
            c.branchEnd(state1be);
            c.iload(posVar);
            c.iload(limVar);
            final BranchEnd uf2 = c.ifIcmpeq();
            c.aload(bufVar);
            c.iload(posVar);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "(I)B");
            c.istore(byteVar);
            c.iinc(posVar, 1);

            c.iload(byteVar);
            c.iconst(10);
            final BranchEnd state2be = c.ifIcmpeq();
            final BranchEnd err2 = c.gotoInstruction();

            // state 2
            c.branchEnd(state2be);
            c.iload(posVar);
            c.iload(limVar);
            final BranchEnd uf3 = c.ifIcmpeq();
            c.aload(bufVar);
            c.iload(posVar);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "(I)B");
            c.istore(byteVar);
            c.iinc(posVar, 1);

            c.iload(byteVar);
            c.invokestatic(classFile.getName(), HM_IS_LWS, "(B)Z");
            c.ifEq(start);
            c.iload(byteVar);
            c.invokestatic(classFile.getName(), HM_IS_HEADER, "(B)Z");
            final BranchEnd err3 = c.ifne();

            // empty header
            // pos is actually located after the first char of the next header
            c.iload(bufVar);
            c.iload(posVar);
            c.iconst(1);
            c.isub();
            c.invokevirtual(ByteBuffer.class.getName(), "position", "(I)Ljava/nio/ByteBuffer;");
            c.pop();

            c.iconst(CONST_OK);
            c.returnInstruction();

            // state 3
            final CodeLocation state3 = c.mark();
            c.branchEnd(state3be);
            c.iload(posVar);
            c.iload(limVar);
            final BranchEnd uf4 = c.ifIcmpeq();
            c.aload(bufVar);
            c.iload(posVar);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "(I)B");
            c.istore(byteVar);
            c.iinc(posVar, 1);

            c.iload(byteVar);
            c.iconst(13);
            final BranchEnd state4be = c.ifIcmpeq();
            c.iload(byteVar);
            c.invokestatic(classFile.getName(), HM_IS_LWS, "(B)Z");
            final BranchEnd state6be1 = c.ifeq();
            c.iload(byteVar);
            c.iconst('"');
            final BranchEnd state7be2 = c.ifIcmpeq();
            c.iload(byteVar);
            c.invokestatic(classFile.getName(), HM_IS_HEADER_VAL, "(B)Z");
            c.ifEq(state3);
            final BranchEnd err4 = c.gotoInstruction();

            // state 4
            final CodeLocation state4 = c.mark();
            c.branchEnd(state4be);
            c.iload(posVar);
            c.iload(limVar);
            final BranchEnd uf5 = c.ifIcmpeq();
            c.aload(bufVar);
            c.iload(posVar);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "(I)B");
            c.istore(byteVar);
            c.iinc(posVar, 1);

            c.iload(byteVar);
            final BranchEnd err5 = c.ifIcmpne();
            // fall thru to...

            // state 5
            c.iload(posVar);
            c.iload(limVar);
            final BranchEnd uf6 = c.ifIcmpeq();
            c.aload(bufVar);
            c.iload(posVar);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "(I)B");
            c.istore(byteVar);
            c.iinc(posVar, 1);

            c.iload(byteVar);
            c.invokestatic(classFile.getName(), HM_IS_LWS, "(B)Z");
            final BranchEnd state6be2 = c.ifeq();
            c.iload(byteVar);
            c.invokestatic(classFile.getName(), HM_IS_HEADER, "(B)Z");
            final BranchEnd err6 = c.ifne();

            // header value ends at the char before this one!
            c.iload(bufVar);
            c.iload(startVar);
            c.invokevirtual(ByteBuffer.class.getName(), "position", "(I)Ljava/nio/ByteBuffer;");
            c.pop();

            c.iload(headerNameVar);
            c.iload(posVar);
            c.iload(startVar);
            c.isub(); // length
            // todo: add hash code for performance
            c.invokestatic("io.undertow.util.HttpString", "fromBytes", "(Ljava/nio/ByteBuffer;I)Lio/undertow/util/HttpString;");
            c.invokevirtual(classFile.getSuperclass(), headerMethod, "(Lio/undertow/util/HttpString;Lio/undertow/util/HttpString;)V");

            c.iload(bufVar);
            c.iload(posVar);
            c.iconst(1);
            c.isub();
            c.invokevirtual(ByteBuffer.class.getName(), "position", "(I)Ljava/nio/ByteBuffer;");
            c.pop();
            c.iconst(CONST_OK);
            c.returnInstruction();

            // state 6
            c.branchEnd(state6be1);
            c.branchEnd(state6be2);
            c.iload(posVar);
            c.iload(limVar);
            final BranchEnd uf7 = c.ifIcmpeq();
            c.aload(bufVar);
            c.iload(posVar);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "(I)B");
            c.istore(byteVar);
            c.iinc(posVar, 1);

            c.iload(byteVar);
            c.iconst(13);
            c.ifIcmpeq(state4);
            c.iload(byteVar);
            c.invokestatic(classFile.getName(), HM_IS_HEADER_VAL, "(B)Z");
            c.ifEq(state3);
            final BranchEnd err7 = c.gotoInstruction();

            // state 7
            c.branchEnd(state7be1);
            c.branchEnd(state7be2);
            final CodeLocation state7 = c.mark();
            c.iload(posVar);
            c.iload(limVar);
            final BranchEnd uf8 = c.ifIcmpeq();
            c.aload(bufVar);
            c.iload(posVar);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "(I)B");
            c.istore(byteVar);
            c.iinc(posVar, 1);

            c.iload(byteVar);
            c.iconst('"');
            c.ifIcmpeq(state3);
            c.iload(byteVar);
            c.iconst('\\');
            final BranchEnd state8be = c.ifeq();
            c.iload(byteVar);
            c.invokestatic(classFile.getName(), HM_IS_HEADER_VAL, "(B)Z");
            c.ifEq(state7);
            final BranchEnd err8 = c.gotoInstruction();

            // state 8
            c.branchEnd(state8be);
            c.iload(posVar);
            c.iload(limVar);
            final BranchEnd uf9 = c.ifIcmpeq();
            c.aload(bufVar);
            c.iload(posVar);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "(I)B");
            c.istore(byteVar);
            c.iinc(posVar, 1);
            // accept all
            c.gotoInstruction(state7);

            c.branchEnd(uf1);
            c.branchEnd(uf2);
            c.branchEnd(uf3);
            c.branchEnd(uf4);
            c.branchEnd(uf5);
            c.branchEnd(uf6);
            c.branchEnd(uf7);
            c.branchEnd(uf8);
            c.branchEnd(uf9);
            c.iconst(CONST_UNDERFLOW);
            c.returnInstruction();

            c.branchEnd(err1);
            c.branchEnd(err2);
            c.branchEnd(err3);
            c.branchEnd(err4);
            c.branchEnd(err5);
            c.branchEnd(err6);
            c.branchEnd(err7);
            c.branchEnd(err8);
            c.iconst(CONST_BAD);
            c.returnInstruction();
        }

    }

    protected void addHeaderParsingStage(final HttpHeaderConfig[] headers, final CodeAttribute c, final Action readRetryAction, final Action badRequestAction, int bufVar, int limVar, String exchangeClass) {
        final ProcessingEnvironment env;
        final RoundEnvironment roundEnv;
        final TypeElement typeElement;
        final StateMachine stateMachine = new StateMachine(readRetryAction, true);
        final CodeMarker headerMarker = new CodeMarker();
        final CodeMarker genericValue = new CodeMarker();
        final CodeMarker ignoreValue = new CodeMarker();
        int singletons = 0;
        int var = 0;
        final int singletonVar = var++;
        final int posVar = var++;
        final int byteVar = var++;
        final int hcVar = var++;
        final int headerNameVar = var++;
        for (HttpHeaderConfig header : headers) {
            // header attributes
            final boolean fastHeader = header.fast();
            if (fastHeader) {
                final String headerName = header.name();
                final boolean singletonHeader = header.singleton();
                final String headerMethodName = header.method();
                // value attributes
                final CaseSensitivity headerCaseSensitive = header.caseSensitive();
                final boolean valueCsv = header.csv();
                final HttpHeaderValueConfig[] values = header.values();
                final HeaderType headerType = header.headerType();
                final int singletonIdx;
                if (singletonHeader) {
                    if (singletons == 64) {
                        env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Too many singleton headers", typeElement);
                        singletonIdx = -1;
                    } else {
                        singletonIdx = singletons++;
                    }
                } else {
                    singletonIdx = -1;
                }
                final Action matchAction;
                matchAction = new ShortAction() {
                    public void emitAction(final CodeAttribute c) {
                        if (singletonIdx > -1) {
                            c.lload(singletonVar);
                            c.lconst(1L << singletonIdx);
                            c.land();
                            ignoreValue.ifEQFrom(c);
                            c.lload(singletonVar);
                            c.lconst(1L << singletonIdx);
                            c.lor();
                            c.lstore(singletonVar);
                        }
                        // first things first
                        c.getfield("io.undertow.util.Headers", headerName.toUpperCase(Locale.US).replace('-', '_'), "Lio/undertow/util/HttpString;");
                        c.astore(headerNameVar);

                        if (headerMethodName != null) {
                            if (headerType == HeaderType.HTTP_STRING) {

                            }

                            c.invokevirtual(className, headerMethodName, "(Ljava/lang/String;)V");
                        } else {
                            genericValue.gotoFrom(c);
                        }


                        // done, get next header
                        headerMarker.gotoFrom(c);
                    }
                };
                stateMachine.addRule(headerName, matchAction);
            }
            // else let it go the slow way 'round for now
        }
        // emit
        headerMarker.mark(c);
        stateMachine.emit(env, sourceElement, c, bufVar, limVar, posVar);
        final CodeMarker terminator = new CodeMarker();
        final CodeMarker headerChar = new CodeMarker();
        // hash table matching
        c.iconst(17);
        c.istore(hcVar);
        c.aload(bufVar);
        c.invokevirtual(ByteBuffer.class.getName(), "position", "()I");
        c.istore(posVar);

        CodeLocation loop = c.mark();
        c.iload(posVar);
        c.iload(limVar);
        readRetryAction.emitIfEQ(c);
        c.aload(bufVar);
        c.iload(posVar);
        c.invokevirtual(ByteBuffer.class.getName(), "get", "(I)B");
        c.istore(byteVar);
        c.iinc(posVar, 1);
        c.iload(byteVar);
        c.invokestatic(resultClass, "isHeaderChar", "(B)Z");
        headerChar.ifNEFrom(c);
        c.iload(byteVar);
        c.invokestatic(resultClass, "isHeaderTerminatorChar", "(B)Z");
        terminator.ifNEFrom(c);
        badRequestAction.emitAction(c);
        headerChar.mark(c);
        c.iload(byteVar);
        // .. b
        c.iconst(0xff);
        // .. b 0xff
        c.iand();
        // .. b'
        c.iload(hcVar);
        // .. b' hc
        c.dup();
        // .. b' hc hc
        c.iconst(4);
        c.ishl();
        // .. b' hc hc*16
        c.iadd();
        // .. b' hc*17
        c.iadd();
        // .. hc'
        c.istore(hcVar);
        c.gotoInstruction(loop);

        terminator.mark(c);
        // got a terminator char
        // arg 1: buffer
        c.aload(bufVar);
        // arg 2: length
        c.iload(posVar);
        c.aload(bufVar);
        c.invokevirtual(ByteBuffer.class.getName(), "position", "()I");
        c.isub();
        // arg 3: hash code
        c.iload(hcVar);
        c.invokestatic("io.undertow.util.HttpString", "fromBytes", "(Ljava/nio/ByteBuffer;II)Lio/undertow/util/HttpString;");
        c.astore(headerNameVar);

        // skip LWS
        genericValue.mark(c);
        c.iload(posVar);
        c.iload(limVar);
        readRetryAction.emitIfEQ(c);
        c.aload(bufVar);
        c.iload(posVar);
        c.invokevirtual(ByteBuffer.class.getName(), "get", "(I)B");
        c.istore(byteVar);
        c.iinc(posVar, 1);
        c.iload(byteVar);
        c.invokestatic(resultClass, "isLwsChar", "(B)Z");
        genericValue.ifEQFrom(c);
        c.iload(byteVar);
        c.invokestatic(resultClass, "isHeaderChar", "(B)Z");

    }

    protected abstract void createStateMachines(final String[] httpVerbs, final String[] httpVersions, final String[] standardHeaders, final String className, final ClassFile file, final ClassMethod sctor, final AtomicInteger fieldCounter);



    protected void createStateMachine(final MatchRule[] rules, final String className, final ClassFile file, final ClassMethod sctor, final AtomicInteger fieldCounter, final String methodName, final CustomStateMachine stateMachine) {

        //list of all states except the initial
        final List<State> allStates = new ArrayList<State>();
        final State initial = new State((byte) 0, "");
        for (String value : originalItems) {
            addStates(initial, value, allStates);
        }
        //we want initial to be number 0
        final AtomicInteger stateCounter = new AtomicInteger(-1);
        setupStateNo(initial, stateCounter, fieldCounter);
        for (State state : allStates) {
            setupStateNo(state, stateCounter, fieldCounter);
            createStateField(state, file, sctor.getCodeAttribute());
        }

        final int noStates = stateCounter.get();

        final ClassMethod handle = file.addMethod(Modifier.PROTECTED | Modifier.FINAL, methodName, "V", DescriptorUtils.makeDescriptor(ByteBuffer.class), parseStateDescriptor, httpExchangeDescriptor);
        writeStateMachine(className, file, handle.getCodeAttribute(), initial, allStates, noStates, stateMachine, sctor);
    }

    private void createStateField(final State state, final ClassFile file, final CodeAttribute sc) {
        if (state.fieldName != null) {
            file.addField(AccessFlag.STATIC | AccessFlag.FINAL | AccessFlag.PRIVATE, state.fieldName, "[B");
            sc.ldc(state.terminalState);
            sc.ldc("ISO-8859-1");
            sc.invokevirtual(String.class.getName(), "getBytes", "(Ljava/lang/String;)[B");
            sc.putstatic(file.getName(), state.fieldName, "[B");
        }
        if (state.httpStringFieldName != null) {
            file.addField(AccessFlag.STATIC | AccessFlag.FINAL | AccessFlag.PRIVATE, state.httpStringFieldName, HTTP_STRING_DESCRIPTOR);

            //first we try and get the string from the map of known HTTP strings
            //this means that the result we store will be the same object as the
            //constants that are referenced in the handlers
            //if this fails we just create a new http string
            sc.aload(CONSTRUCTOR_HTTP_STRING_MAP_VAR);
            if (state.terminalState != null) {
                sc.ldc(state.terminalState);
            } else {
                sc.ldc(state.soFar);
            }
            sc.invokeinterface(Map.class.getName(), "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
            sc.dup();
            BranchEnd end = sc.ifnull();
            sc.checkcast(HTTP_STRING_CLASS);
            sc.putstatic(file.getName(), state.httpStringFieldName, HTTP_STRING_DESCRIPTOR);
            BranchEnd done = sc.gotoInstruction();
            sc.branchEnd(end);
            sc.pop();
            sc.newInstruction(HTTP_STRING_CLASS);
            sc.dup();
            if (state.terminalState != null) {
                sc.ldc(state.terminalState);
            } else {
                sc.ldc(state.soFar);
            }
            sc.invokespecial(HTTP_STRING_CLASS, "<init>", "(Ljava/lang/String;)V");
            sc.putstatic(file.getName(), state.httpStringFieldName, HTTP_STRING_DESCRIPTOR);
            sc.branchEnd(done);
        }
    }


    private void setupStateNo(final State state, final AtomicInteger stateCounter, final AtomicInteger fieldCounter) {
        if (state.next.isEmpty()) {
            state.stateno = PREFIX_MATCH;
            state.terminalState = state.soFar;
            state.fieldName = "STATE_BYTES_" + fieldCounter.incrementAndGet();
        } else if (state.next.size() == 1) {
            String terminal = null;
            State s = state.next.values().iterator().next();
            while (true) {
                if (s.next.size() > 1) {
                    break;
                } else if (s.next.isEmpty()) {
                    terminal = s.soFar;
                    break;
                }
                s = s.next.values().iterator().next();
            }
            if (terminal != null) {
                state.stateno = PREFIX_MATCH;
                state.terminalState = terminal;
                state.fieldName = "STATE_BYTES_" + fieldCounter.incrementAndGet();
            } else {
                state.stateno = stateCounter.incrementAndGet();
            }
        } else {
            state.stateno = stateCounter.incrementAndGet();
        }
        state.httpStringFieldName = "HTTP_STRING_" + fieldCounter.incrementAndGet();
    }

    private void writeStateMachine(final String className, final ClassFile file, final CodeAttribute c, final State initial, final List<State> allStates, int noStates, final CustomStateMachine stateMachine, final ClassMethod sctor) {

        //initial hasRemaining check
        c.aload(BYTE_BUFFER_VAR);
        c.invokevirtual(ByteBuffer.class.getName(), "hasRemaining", "()Z");
        final BranchEnd nonZero = c.ifne();
        //we have run out of bytes, return 0
        c.iconst(0);
        c.returnInstruction();

        c.branchEnd(nonZero);


        final List<State> states = new ArrayList<State>();
        states.add(initial);
        states.addAll(allStates);
        Collections.sort(states);

        //store the current state in a local variable
        c.aload(PARSE_STATE_VAR);
        c.dup();
        c.getfield(parseStateClass, "stringBuilder", DescriptorUtils.makeDescriptor(StringBuilder.class));
        c.astore(STATE_STRING_BUILDER_VAR);
        c.dup();
        c.getfield(parseStateClass, "parseState", "I");
        c.dup();
        c.istore(CURRENT_STATE_VAR);
        //if this is state 0 there is a lot of stuff can ignore
        BranchEnd optimizationEnd = c.ifeq();
        c.dup();
        c.getfield(parseStateClass, "pos", "I");
        c.istore(STATE_POS_VAR);
        c.dup();
        c.getfield(parseStateClass, "current", HTTP_STRING_DESCRIPTOR);
        c.astore(STATE_CURRENT_VAR);
        c.getfield(parseStateClass, "currentBytes", "[B");
        c.astore(STATE_CURRENT_BYTES_VAR);


        //load the current state
        c.iload(CURRENT_STATE_VAR);
        //switch on the current state
        TableSwitchBuilder builder = new TableSwitchBuilder(-2, noStates);
        final IdentityHashMap<State, AtomicReference<BranchEnd>> ends = new IdentityHashMap<State, AtomicReference<BranchEnd>>();
        final AtomicReference<BranchEnd> prefixMatch = builder.add();
        final AtomicReference<BranchEnd> noState = builder.add();

        ends.put(initial, builder.add());
        for (final State s : states) {
            if (s.stateno > 0) {
                ends.put(s, builder.add());
            }
        }
        c.tableswitch(builder);
        stateNotFound(c, builder);

        //return code
        //code that synchronizes the state object and returns
        setupLocalVariables(c);
        final CodeLocation returnIncompleteCode = c.mark();
        c.aload(PARSE_STATE_VAR);
        c.dup();
        c.dup();
        c.dup();
        c.dup();

        c.iload(STATE_POS_VAR);
        c.putfield(parseStateClass, "pos", "I");
        c.aload(STATE_CURRENT_VAR);
        c.putfield(parseStateClass, "current", HTTP_STRING_DESCRIPTOR);
        c.aload(STATE_CURRENT_BYTES_VAR);
        c.putfield(parseStateClass, "currentBytes", "[B");
        c.iload(CURRENT_STATE_VAR);
        c.putfield(parseStateClass, "parseState", "I");
        c.returnInstruction();
        setupLocalVariables(c);
        final CodeLocation returnCompleteCode = c.mark();
        c.aload(PARSE_STATE_VAR);
        c.dup();
        c.dup();
        c.dup();
        c.dup();

        c.iconst(0);
        c.putfield(parseStateClass, "pos", "I");
        c.aconstNull();
        c.putfield(parseStateClass, "current", HTTP_STRING_DESCRIPTOR);
        c.aconstNull();
        c.putfield(parseStateClass, "currentBytes", "[B");
        c.aload(STATE_STRING_BUILDER_VAR);
        c.iconst(0);
        c.invokevirtual(StringBuilder.class.getName(), "setLength", "(I)V");
        c.iconst(0);
        c.putfield(parseStateClass, "parseState", "I");
        c.returnInstruction();

        //prefix
        c.branchEnd(prefixMatch.get());

        final CodeLocation prefixLoop = c.mark(); //loop for when we are prefix matching
        handleReturnIfNoMoreBytes(c, returnIncompleteCode);
        //load 3 copies of the current byte into the stack
        c.aload(BYTE_BUFFER_VAR);
        c.invokevirtual(ByteBuffer.class.getName(), "get", "()B");
        c.dup();
        c.dup();
        final Set<BranchEnd> prefixHandleSpace = new HashSet<BranchEnd>();
        if (stateMachine.isHeader()) {
            c.iconst(':');
            prefixHandleSpace.add(c.ifIcmpeq());
            c.dup();
        }
        c.iconst(' ');
        prefixHandleSpace.add(c.ifIcmpeq());
        c.dup();
        c.iconst('\t');
        prefixHandleSpace.add(c.ifIcmpeq());
        c.dup();
        c.iconst('\r');
        prefixHandleSpace.add(c.ifIcmpeq());
        c.dup();
        c.iconst('\n');
        prefixHandleSpace.add(c.ifIcmpeq());
        //check if we have overrun
        c.aload(STATE_CURRENT_BYTES_VAR);
        c.arraylength();
        c.iload(STATE_POS_VAR);
        BranchEnd overrun = c.ifIcmpeq();
        //so we have not overrun
        //now check if the character matches
        c.dup();
        c.aload(STATE_CURRENT_BYTES_VAR);
        c.iload(STATE_POS_VAR);
        c.baload();
        c.isub();
        BranchEnd noMatch = c.ifne();

        //so they match
        c.pop2(); //pop our extra bytes off the stack, we do not need it
        c.iinc(STATE_POS_VAR, 1);
        handleReturnIfNoMoreBytes(c, returnIncompleteCode);
        c.gotoInstruction(prefixLoop);

        c.branchEnd(overrun); //overrun and not match use the same code path
        c.branchEnd(noMatch); //the current character did not match
        c.iconst(INITIAL);
        c.istore(CURRENT_STATE_VAR);

        //create the string builder
        c.aload(STATE_STRING_BUILDER_VAR);
        c.aload(STATE_CURRENT_VAR);
        c.invokevirtual(HTTP_STRING_CLASS, "toString", "()Ljava/lang/String;");
        c.iconst(0);
        c.iload(STATE_POS_VAR);
        c.invokevirtual(String.class.getName(), "substring", "(II)Ljava/lang/String;");
        c.invokevirtual(StringBuilder.class.getName(), "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        c.swap();

        c.invokevirtual(StringBuilder.class.getName(), "append", "(C)Ljava/lang/StringBuilder;");
        c.pop2();
        BranchEnd prefixToNoState = c.gotoInstruction();

        //handle the space case
        for (BranchEnd b : prefixHandleSpace) {
            c.branchEnd(b);
        }

        //new state will be 0
        c.iconst(0);
        c.istore(CURRENT_STATE_VAR);

        c.aload(STATE_CURRENT_BYTES_VAR);
        c.arraylength();
        c.iload(STATE_POS_VAR);
        BranchEnd correctLength = c.ifIcmpeq();

        c.newInstruction(HTTP_STRING_CLASS);
        c.dup();
        c.aload(STATE_CURRENT_BYTES_VAR);
        c.iconst(0);
        c.iload(STATE_POS_VAR);
        c.invokespecial(HTTP_STRING_CLASS, "<init>", "([BII)V");
        stateMachine.handleOtherToken(c);
        //TODO: exit if it returns null
        //decrease the available bytes
        c.pop();
        tokenDone(c, returnCompleteCode, stateMachine);

        c.branchEnd(correctLength);

        c.aload(STATE_CURRENT_VAR);
        stateMachine.handleStateMachineMatchedToken(c);
        //TODO: exit if it returns null
        c.pop();
        tokenDone(c, returnCompleteCode, stateMachine);


        //nostate
        c.branchEnd(noState.get());
        c.branchEnd(prefixToNoState);
        CodeLocation noStateLoop = c.mark();

        handleReturnIfNoMoreBytes(c, returnIncompleteCode);
        //load 2 copies of the current byte into the stack
        c.aload(BYTE_BUFFER_VAR);
        c.invokevirtual(ByteBuffer.class.getName(), "get", "()B");
        c.dup();

        final Set<BranchEnd> nostateHandleSpace = new HashSet<BranchEnd>();
        if (stateMachine.isHeader()) {
            c.iconst(':');
            nostateHandleSpace.add(c.ifIcmpeq());
            c.dup();
        }
        c.iconst(' ');
        nostateHandleSpace.add(c.ifIcmpeq());
        c.dup();
        c.iconst('\t');
        nostateHandleSpace.add(c.ifIcmpeq());
        c.dup();
        c.iconst('\r');
        nostateHandleSpace.add(c.ifIcmpeq());
        c.dup();
        c.iconst('\n');
        nostateHandleSpace.add(c.ifIcmpeq());
        c.aload(STATE_STRING_BUILDER_VAR);
        c.swap();
        c.invokevirtual(StringBuilder.class.getName(), "append", "(C)Ljava/lang/StringBuilder;");
        c.pop();
        c.aload(BYTE_BUFFER_VAR);
        c.invokevirtual(ByteBuffer.class.getName(), "hasRemaining", "()Z");
        c.ifne(noStateLoop); //go back to the start if we have not run out of bytes

        //we have run out of bytes, so we need to write back the current state
        c.aload(PARSE_STATE_VAR);
        c.iload(CURRENT_STATE_VAR);
        c.putfield(parseStateClass, "parseState", "I");
        c.iconst(0);
        c.returnInstruction();
        for (BranchEnd b : nostateHandleSpace) {
            c.branchEnd(b);
        }
        c.aload(STATE_STRING_BUILDER_VAR);
        c.invokevirtual(StringBuilder.class.getName(), "toString", "()Ljava/lang/String;");

        c.newInstruction(HTTP_STRING_CLASS);
        c.dupX1();
        c.swap();
        c.invokespecial(HTTP_STRING_CLASS, "<init>", "(Ljava/lang/String;)V");
        stateMachine.handleOtherToken(c);
        //TODO: exit if it returns null
        tokenDone(c, returnCompleteCode, stateMachine);

        c.branchEnd(optimizationEnd);
        c.pop();
        c.iconst(0);
        c.istore(STATE_POS_VAR);
        c.aconstNull();
        c.astore(STATE_CURRENT_VAR);
        c.aconstNull();
        c.astore(STATE_CURRENT_BYTES_VAR);

        c.branchEnd(ends.get(initial).get());
        invokeState(className, file, c, initial, initial, noStateLoop, prefixLoop, returnIncompleteCode, returnCompleteCode, stateMachine);
        for (final State s : allStates) {
            if (s.stateno >= 0) {
                c.branchEnd(ends.get(s).get());
                invokeState(className, file, c, s, initial, noStateLoop, prefixLoop, returnIncompleteCode, returnCompleteCode, stateMachine);
            }
        }

    }

    private void setupLocalVariables(final CodeAttribute c) {
        c.setupFrame(DescriptorUtils.makeDescriptor("fakeclass"),
                DescriptorUtils.makeDescriptor(ByteBuffer.class),
                parseStateDescriptor,
                httpExchangeDescriptor,
                "I",
                "I",
                DescriptorUtils.makeDescriptor(String.class),
                DescriptorUtils.makeDescriptor(StringBuilder.class),
                "[B");
    }

    private void handleReturnIfNoMoreBytes(final CodeAttribute c, final CodeLocation returnCode) {
        c.aload(BYTE_BUFFER_VAR);
        c.invokevirtual(ByteBuffer.class.getName(), "hasRemaining", "()Z");
        c.ifEq(returnCode); //go back to the start if we have not run out of bytes
    }

    private void tokenDone(final CodeAttribute c, final CodeLocation returnCode, final CustomStateMachine stateMachine) {
        stateMachine.updateParseState(c);
        c.gotoInstruction(returnCode);
    }

    private void invokeState(final String className, final ClassFile file, final CodeAttribute c, final State currentState, final State initialState, final CodeLocation noStateStart, final CodeLocation prefixStart, final CodeLocation returnIncompleteCode, final CodeLocation returnCompleteCode, final CustomStateMachine stateMachine) {
        currentState.mark(c);

        BranchEnd parseDone = null;

        if (currentState == initialState) {
            //if this is the initial state there is a possibility that we need to deal with a left over character first
            //we need to see if we start with a left over character
            c.aload(PARSE_STATE_VAR);
            c.getfield(parseStateClass, "leftOver", "B");
            c.dup();
            final BranchEnd end = c.ifne();
            c.pop();
            //load 2 copies of the current byte into the stack
            handleReturnIfNoMoreBytes(c, returnIncompleteCode);
            c.aload(BYTE_BUFFER_VAR);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "()B");
            BranchEnd cont = c.gotoInstruction();
            c.branchEnd(end);
            c.aload(PARSE_STATE_VAR);
            c.iconst(0);
            c.putfield(parseStateClass, "leftOver", "B");

            c.branchEnd(cont);

        } else {
            handleReturnIfNoMoreBytes(c, returnIncompleteCode);
            //load 2 copies of the current byte into the stack
            c.aload(BYTE_BUFFER_VAR);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "()B");
        }

        c.dup();
        final Set<AtomicReference<BranchEnd>> tokenEnds = new HashSet<AtomicReference<BranchEnd>>();
        final Map<State, AtomicReference<BranchEnd>> ends = new IdentityHashMap<State, AtomicReference<BranchEnd>>();
        for (State state : currentState.next.values()) {
            c.iconst(state.value);
            ends.put(state, new AtomicReference<BranchEnd>(c.ifIcmpeq()));
            c.dup();
        }
        if (stateMachine.isHeader()) {
            c.iconst(':');
            tokenEnds.add(new AtomicReference<BranchEnd>(c.ifIcmpeq()));
            c.dup();
        }
        c.iconst(' ');
        tokenEnds.add(new AtomicReference<BranchEnd>(c.ifIcmpeq()));
        c.dup();
        c.iconst('\t');
        tokenEnds.add(new AtomicReference<BranchEnd>(c.ifIcmpeq()));
        c.dup();
        c.iconst('\r');
        tokenEnds.add(new AtomicReference<BranchEnd>(c.ifIcmpeq()));
        c.dup();
        c.iconst('\n');
        tokenEnds.add(new AtomicReference<BranchEnd>(c.ifIcmpeq()));


        c.iconst(INITIAL);
        c.istore(CURRENT_STATE_VAR);

        //create the string builder
        c.aload(STATE_STRING_BUILDER_VAR);
        c.ldc(currentState.soFar);
        c.invokevirtual(StringBuilder.class.getName(), "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        c.swap();
        c.invokevirtual(StringBuilder.class.getName(), "append", "(C)Ljava/lang/StringBuilder;");
        c.pop();

        c.gotoInstruction(noStateStart);

        //now we write out tokenEnd
        for (AtomicReference<BranchEnd> tokenEnd : tokenEnds) {
            c.branchEnd(tokenEnd.get());
        }

        if (!currentState.soFar.equals("")) {
            c.getstatic(file.getName(), currentState.httpStringFieldName, HTTP_STRING_DESCRIPTOR);
            stateMachine.handleStateMachineMatchedToken(c);
            //TODO: exit if it returns null
            tokenDone(c, returnCompleteCode, stateMachine);
        } else {
            if (stateMachine.initialNewlineMeansRequestDone()) {
                c.iconst('\n');
                parseDone = c.ifIcmpeq();
            } else {
                c.pop();
            }
            setupLocalVariables(c);
            handleReturnIfNoMoreBytes(c, returnIncompleteCode);
            initialState.jumpTo(c);
        }

        for (Map.Entry<State, AtomicReference<BranchEnd>> e : ends.entrySet()) {
            c.branchEnd(e.getValue().get());
            c.pop();
            final State state = e.getKey();
            if (state.stateno < 0) {
                //prefix match
                c.iconst(state.stateno);
                c.istore(CURRENT_STATE_VAR);
                c.getstatic(className, state.httpStringFieldName, HTTP_STRING_DESCRIPTOR);
                c.astore(STATE_CURRENT_VAR);
                c.getstatic(className, state.fieldName, "[B");
                c.astore(STATE_CURRENT_BYTES_VAR);
                c.iconst(state.soFar.length());
                c.istore(STATE_POS_VAR);
                c.gotoInstruction(prefixStart);
            } else {

                c.iconst(state.stateno);
                c.istore(CURRENT_STATE_VAR);
                state.jumpTo(c);
            }
        }
        if (parseDone != null) {
            c.branchEnd(parseDone);

            c.aload(PARSE_STATE_VAR);
            c.invokevirtual(parseStateClass, "parseComplete", "()V");
            c.iconst(0);
            c.returnInstruction();
        }
    }

    /**
     * Throws an exception when an invalid state is hit in a tableswitch
     */
    private static void stateNotFound(final CodeAttribute c, final TableSwitchBuilder builder) {
        c.branchEnd(builder.getDefaultBranchEnd().get());
        c.newInstruction(RuntimeException.class);
        c.dup();
        c.ldc("Invalid character");
        c.invokespecial(RuntimeException.class.getName(), "<init>", "(Ljava/lang/String;)V");
        c.athrow();
    }

    private static void addStates(final State initial, final String value, final List<State> allStates) {
        addStates(initial, value, 0, allStates);
    }

    private static void addStates(final State current, final String value, final int i, final List<State> allStates) {
        if (i == value.length()) {
            current.finalState = true;
            return;
        }
        byte[] bytes = value.getBytes();
        final byte currentByte = bytes[i];
        State newState = current.next.get(currentByte);
        if (newState == null) {
            current.next.put(currentByte, newState = new State(currentByte, value.substring(0, i + 1)));
            allStates.add(newState);
        }
        addStates(newState, value, i + 1, allStates);
    }

    private static class State implements Comparable<State> {

        Integer stateno;
        String terminalState;
        String fieldName;
        String httpStringFieldName;
        /**
         * If this state represents a possible final state
         */
        boolean finalState;
        final byte value;
        final String soFar;
        final Map<Byte, State> next = new HashMap<Byte, State>();
        private final Set<BranchEnd> branchEnds = new HashSet<BranchEnd>();
        private CodeLocation location;

        private State(final byte value, final String soFar) {
            this.value = value;
            this.soFar = soFar;
        }

        @Override
        public int compareTo(final State o) {
            return stateno.compareTo(o.stateno);
        }

        void mark(final CodeAttribute ca) {
            location = ca.mark();
            for (BranchEnd br : branchEnds) {
                ca.branchEnd(br);
            }
        }

        void jumpTo(final CodeAttribute ca) {
            if (location == null) {
                branchEnds.add(ca.gotoInstruction());
            } else {
                ca.gotoInstruction(location);
            }
        }

        void ifne(final CodeAttribute ca) {
            if (location == null) {
                branchEnds.add(ca.ifne());
            } else {
                ca.ifne(location);
            }
        }
    }

    /**
     * A class that separates out the different behaviour of the three state machines (VERB, VERSION and HEADER)
     */
    public interface CustomStateMachine {

        boolean isHeader();

        void handleStateMachineMatchedToken(final CodeAttribute c);

        void handleOtherToken(final CodeAttribute c);

        void updateParseState(CodeAttribute c);

        boolean initialNewlineMeansRequestDone();
    }


}

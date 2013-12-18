/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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

import static java.lang.Integer.signum;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import org.jboss.classfilewriter.code.CodeAttribute;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class StateMachine {
    private final List<MatchRule> rules = new ArrayList<>();
    private final Action underflowAction;
    private final boolean ignoreCase;

    public StateMachine(final Action underflowAction, final boolean ignoreCase) {
        this.underflowAction = underflowAction;
        // this action should set the new buffer position to pos
        this.ignoreCase = ignoreCase;
    }

    public void addRule(String text, Action action) {
        rules.add(new MatchRule(text, action));
    }

    public void emit(ProcessingEnvironment env, Element sourceElement, CodeAttribute c, int bufVar, int limVar, int posVar) {
        final Messager messager = env.getMessager();
        final TreeMap<Byte, State> first = new TreeMap<>();
        final CodeMarker firstMarker = new CodeMarker();
        final CodeMarker failMarker = new CodeMarker();
        firstMarker.mark(c);
        for (MatchRule rule : rules) {
            rule.addToState(messager, sourceElement, first, ignoreCase ? CASE_INSENSITIVE : CASE_SENSITIVE);
        }
        emitByteCode(c, failMarker, first, bufVar, limVar, posVar);
        // first rule failed to match
        failMarker.mark(c);
    }

    private void emitDelimCheck(CodeAttribute c, Action delimAction) {
        char[] delimiters = null;
        for (char delimiter : delimiters) {
            c.iconst(delimiter);
            delimAction.emitIfEQ(c);
        }
        // fall out of match
    }

    private void emitByteCode(CodeAttribute c, CodeMarker failMarker, TreeMap<Byte, State> map, int bufVar, int limVar, int posVar) {
        for (Byte bx : map.keySet()) {
            final State s = map.get(bx);
            byte b = bx.byteValue();
            // check limit
            c.iload(posVar);
            c.iload(limVar);
            underflowAction.emitIfEQ(c);
            c.load(ByteBuffer.class, bufVar);
            c.iload(posVar);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "(I)B");
            c.iinc(posVar, 1);
            // byte on stack
            final CodeMarker mark = new CodeMarker();
            final CodeMarker matchMark = new CodeMarker();
            if (ignoreCase && (b >= 'a' && b <= 'z' || b >= 'A' && b <= 'Z')) {
                c.iconst(b);
                // if equal, go to the match action else fall thru to next compare
                matchMark.ifEQFrom(c);
                b ^= 0x20;
            }
            c.iconst(b);
            mark.ifNEFrom(c);
            matchMark.mark(c);
            emitByteCode(c, failMarker, s.next, bufVar, limVar, posVar);
            if (s.action != null) {
                // no bytes matched; check for delimiters for matched action
                emitDelimCheck(c, s.action);
            }
            // no DFA matches for the just-emitted string
            failMarker.gotoFrom(c);
            // mark next string
            mark.mark(c);
        }
    }

    public static final Comparator<Byte> CASE_SENSITIVE = new Comparator<Byte>() {
        public int compare(final Byte o1, final Byte o2) {
            return signum(o1.byteValue() - o2.byteValue());
        }
    };

    public static final Comparator<Byte> CASE_INSENSITIVE = new Comparator<Byte>() {
        public int compare(final Byte o1, final Byte o2) {
            byte b1 = o1.byteValue();
            byte b2 = o2.byteValue();
            if (b1 >= 'a' && b1 <= 'z') b1 &= ~0x20;
            if (b2 >= 'a' && b2 <= 'z') b2 &= ~0x20;
            return signum(b1 - b2);
        }
    };

    static class State {
        // if {@code null}, this is not a terminal state
        Action action;
        final byte value;
        final TreeMap<Byte, State> next;

        State(final byte value, final Comparator<Byte> comparator) {
            this.value = value;
            next = new TreeMap<>(comparator);
        }
    }
}

package io.undertow.annotationprocessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.classfilewriter.ClassFile;
import org.jboss.classfilewriter.ClassMethod;
import org.jboss.classfilewriter.code.CodeAttribute;

/**
 * @author Stuart Douglas
 */
public class RequestParserGenerator extends AbstractParserGenerator {

    public static final String PARSE_STATE_CLASS = "io.undertow.server.protocol.http.ParseState";
    public static final String HTTP_EXCHANGE_CLASS = "io.undertow.server.HttpServerExchange";


    //parsing states
    public static final int VERB = 0;
    public static final int PATH = 1;
    public static final int PATH_PARAMETERS = 2;
    public static final int QUERY_STRING = 3;
    public static final int VERSION = 4;
    public static final int AFTER_VERSION = 5;
    public static final int HEADER = 6;
    public static final int HEADER_VALUE = 7;

    public RequestParserGenerator() {
        super(PARSE_STATE_CLASS, HTTP_EXCHANGE_CLASS, "(Lorg/xnio/OptionMap;)V");
    }

    protected void createStateMachines(final String[] httpVerbs, final String[] httpVersions, final HttpHeaderConfig[] standardHeaders, final String className, final ClassFile file, final ClassMethod sctor, final AtomicInteger fieldCounter) {
        final List<MatchRule> rules = new ArrayList<MatchRule>();
        for (HttpHeaderConfig header : standardHeaders) {
            final MatchAction action;
            final String terminators;
            if (header.csv() ){

            }
            action = new MatchAction() {
                public void emitAction(final CodeAttribute code) {

                }
            };
            rules.add(new MatchRule(header.name(), action));
        }
        createStateMachine(httpVerbs, className, file, sctor, fieldCounter, HANDLE_HTTP_VERB, new VerbStateMachine());
        createStateMachine(httpVersions, className, file, sctor, fieldCounter, HANDLE_HTTP_VERSION, new VersionStateMachine());
        createStateMachine(standardHeaders, className, file, sctor, fieldCounter, HANDLE_HEADER, new HeaderStateMachine());
    }


    protected class HeaderStateMachine implements CustomStateMachine {

        @Override
        public boolean isHeader() {
            return true;
        }

        @Override
        public void handleOtherToken(final CodeAttribute c) {
            c.aload(PARSE_STATE_VAR);
            c.swap();
            c.putfield(parseStateClass, "nextHeader", HTTP_STRING_DESCRIPTOR);
        }

        @Override
        public void handleStateMachineMatchedToken(final CodeAttribute c) {
            c.aload(PARSE_STATE_VAR);
            c.swap();
            c.putfield(parseStateClass, "nextHeader", HTTP_STRING_DESCRIPTOR);
        }

        @Override
        public void updateParseState(final CodeAttribute c) {
            c.pop();
            c.aload(PARSE_STATE_VAR);
            c.iconst(HEADER_VALUE);
            c.putfield(parseStateClass, "state", "I");
        }

        @Override
        public boolean initialNewlineMeansRequestDone() {
            return true;
        }
    }


    protected class VersionStateMachine implements CustomStateMachine {

        @Override
        public boolean isHeader() {
            return false;
        }

        @Override
        public void handleOtherToken(final CodeAttribute c) {
            c.aload(HTTP_RESULT);
            c.swap();
            c.invokevirtual(resultClass, "setProtocol", "(" + HTTP_STRING_DESCRIPTOR + ")V");
        }

        @Override
        public void handleStateMachineMatchedToken(final CodeAttribute c) {
            c.aload(HTTP_RESULT);
            c.swap();
            c.invokevirtual(resultClass, "setProtocol", "(" + HTTP_STRING_DESCRIPTOR + ")V");
        }

        @Override
        public void updateParseState(final CodeAttribute c) {
            c.aload(PARSE_STATE_VAR);
            c.swap();
            c.putfield(parseStateClass, "leftOver", "B");
            c.aload(PARSE_STATE_VAR);
            c.iconst(AFTER_VERSION);
            c.putfield(parseStateClass, "state", "I");
        }

        @Override
        public boolean initialNewlineMeansRequestDone() {
            return false;
        }

    }

    private class VerbStateMachine implements CustomStateMachine {

        @Override
        public boolean isHeader() {
            return false;
        }

        @Override
        public void handleStateMachineMatchedToken(final CodeAttribute c) {
            c.aload(HTTP_RESULT);
            c.swap();
            c.invokevirtual(resultClass, "setRequestMethod", "(" + HTTP_STRING_DESCRIPTOR + ")V");
        }

        @Override
        public void handleOtherToken(final CodeAttribute c) {
            c.aload(HTTP_RESULT);
            c.swap();
            c.invokevirtual(resultClass, "setRequestMethod", "(" + HTTP_STRING_DESCRIPTOR + ")V");
        }

        @Override
        public void updateParseState(final CodeAttribute c) {
            c.pop();
            c.aload(PARSE_STATE_VAR);
            c.iconst(PATH);
            c.putfield(parseStateClass, "state", "I");
        }

        @Override
        public boolean initialNewlineMeansRequestDone() {
            return false;
        }
    }
}

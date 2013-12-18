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

package io.undertow.server.protocol.http;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.annotationprocessor.HttpHeaderConfig;
import io.undertow.annotationprocessor.HttpHeaderValueConfig;
import io.undertow.annotationprocessor.HttpParserConfig;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import io.undertow.util.URLUtils;
import org.xnio.OptionMap;

import static io.undertow.annotationprocessor.CaseSensitivity.*;

import static io.undertow.util.Headers.*;
import static io.undertow.util.Methods.*;
import static io.undertow.util.Protocols.*;

/**
 * The basic HTTP parser. The actual parser is a sub class of this class that is generated as part of
 * the build process by the {@link io.undertow.annotationprocessor.AbstractParserGenerator} annotation processor.
 * <p/>
 * The actual processor is a state machine, that means that for common header, method, protocol values
 * it will return an interned string, rather than creating a new string for each one.
 * <p/>
 *
 * @author Stuart Douglas
 */
@HttpParserConfig(
    defaultHeaderMethod = "handleHeader",
    fastMethods = {
        OPTIONS_STRING,
        GET_STRING,
        HEAD_STRING,
        POST_STRING,
        PUT_STRING,
        DELETE_STRING,
        TRACE_STRING,
        CONNECT_STRING
    },
    fastProtocols = {
        HTTP_0_9_STRING,
        HTTP_1_0_STRING,
        HTTP_1_1_STRING
    },
    headers = {
        @HttpHeaderConfig(name = ACCEPT_STRING, csv = true, fast = true),
        @HttpHeaderConfig(name = ACCEPT_CHARSET_STRING, csv = true),
        @HttpHeaderConfig(name = ACCEPT_ENCODING_STRING, csv = true, fast = true, caseSensitive = CASE_INSENSITIVE, values = {
            @HttpHeaderValueConfig(name = COMPRESS_STRING),
            @HttpHeaderValueConfig(name = X_COMPRESS_STRING),
            @HttpHeaderValueConfig(name = DEFLATE_STRING),
            @HttpHeaderValueConfig(name = GZIP_STRING),
            @HttpHeaderValueConfig(name = X_GZIP_STRING),
            @HttpHeaderValueConfig(name = IDENTITY_STRING),
            @HttpHeaderValueConfig(name = SDCH_STRING)
        }),
        @HttpHeaderConfig(name = ACCEPT_LANGUAGE_STRING, csv = true, fast = true, caseSensitive = CASE_INSENSITIVE),
        @HttpHeaderConfig(name = ACCEPT_RANGES_STRING, csv = true),
        @HttpHeaderConfig(name = AUTHORIZATION_STRING),
        @HttpHeaderConfig(name = CACHE_CONTROL_STRING, csv = true, fast = true),
        @HttpHeaderConfig(name = COOKIE_STRING, fast = true, method = "handleCookie"),
        @HttpHeaderConfig(name = CONNECTION_STRING, csv = true, fast = true, caseSensitive = CASE_INSENSITIVE, values = {
            @HttpHeaderValueConfig(name = CLOSE_STRING),
            @HttpHeaderValueConfig(name = KEEP_ALIVE_STRING)
        }),
        @HttpHeaderConfig(name = CONTENT_ENCODING_STRING, csv = true, caseSensitive = CASE_INSENSITIVE, values = {
            // this value is not really allowed, but by putting it here we can combine parser actions and optimize better
            @HttpHeaderValueConfig(name = CHUNKED_STRING),
            @HttpHeaderValueConfig(name = COMPRESS_STRING),
            @HttpHeaderValueConfig(name = X_COMPRESS_STRING),
            @HttpHeaderValueConfig(name = DEFLATE_STRING),
            @HttpHeaderValueConfig(name = GZIP_STRING),
            @HttpHeaderValueConfig(name = X_GZIP_STRING),
            @HttpHeaderValueConfig(name = IDENTITY_STRING),
            @HttpHeaderValueConfig(name = SDCH_STRING)
        }),
        @HttpHeaderConfig(name = CONTENT_LENGTH_STRING, method = "handleContentLength", singleton = true),
        @HttpHeaderConfig(name = CONTENT_TYPE_STRING, singleton = true),
        @HttpHeaderConfig(name = EXPECT_STRING, csv = true, caseSensitive = CASE_INSENSITIVE_EXCEPT_QUOTED),
        @HttpHeaderConfig(name = FROM_STRING),
        @HttpHeaderConfig(name = HOST_STRING, fast = true, method = "handleHost", singleton = true, caseSensitive = CASE_INSENSITIVE, values = {
            @HttpHeaderValueConfig(name = LOCALHOST_STRING)
        }),
        @HttpHeaderConfig(name = IF_MATCH_STRING, csv = true),
        @HttpHeaderConfig(name = IF_MODIFIED_SINCE_STRING, fast = true),
        @HttpHeaderConfig(name = IF_NONE_MATCH_STRING, csv = true, fast = true),
        @HttpHeaderConfig(name = IF_RANGE_STRING),
        @HttpHeaderConfig(name = IF_UNMODIFIED_SINCE_STRING),
        @HttpHeaderConfig(name = MAX_FORWARDS_STRING),
        @HttpHeaderConfig(name = ORIGIN_STRING),
        @HttpHeaderConfig(name = PRAGMA_STRING, csv = true, fast = true, caseSensitive = CASE_INSENSITIVE_EXCEPT_QUOTED, values = {
            @HttpHeaderValueConfig(name = NO_CACHE_STRING)
        }),
        @HttpHeaderConfig(name = PROXY_AUTHORIZATION_STRING),
        @HttpHeaderConfig(name = RANGE_STRING),
        @HttpHeaderConfig(name = REFERER_STRING, fast = true),
        @HttpHeaderConfig(name = REFRESH_STRING),
        @HttpHeaderConfig(name = SEC_WEB_SOCKET_KEY_STRING),
        @HttpHeaderConfig(name = SEC_WEB_SOCKET_VERSION_STRING),
        @HttpHeaderConfig(name = SERVER_STRING),
        @HttpHeaderConfig(name = SSL_CLIENT_CERT_STRING),
        @HttpHeaderConfig(name = SSL_CIPHER_STRING),
        @HttpHeaderConfig(name = SSL_SESSION_ID_STRING),
        @HttpHeaderConfig(name = SSL_CIPHER_USEKEYSIZE_STRING),
        @HttpHeaderConfig(name = STRICT_TRANSPORT_SECURITY_STRING),
        @HttpHeaderConfig(name = TE_STRING, csv = true),
        @HttpHeaderConfig(name = TRAILER_STRING, csv = true, caseSensitive = CASE_INSENSITIVE),
        @HttpHeaderConfig(name = TRANSFER_ENCODING_STRING, csv = true, fast = true, method = "handleTransferEncoding", caseSensitive = CASE_INSENSITIVE, values = {
            @HttpHeaderValueConfig(name = CHUNKED_STRING),
            @HttpHeaderValueConfig(name = COMPRESS_STRING),
            @HttpHeaderValueConfig(name = X_COMPRESS_STRING),
            @HttpHeaderValueConfig(name = DEFLATE_STRING),
            @HttpHeaderValueConfig(name = GZIP_STRING),
            @HttpHeaderValueConfig(name = X_GZIP_STRING),
            @HttpHeaderValueConfig(name = IDENTITY_STRING),
            @HttpHeaderValueConfig(name = SDCH_STRING)
        }),
        @HttpHeaderConfig(name = UPGRADE_STRING, csv = true),
        @HttpHeaderConfig(name = USER_AGENT_STRING, fast = true),
        @HttpHeaderConfig(name = VIA_STRING, csv = true),
        @HttpHeaderConfig(name = WARNING_STRING, csv = true)
    })
public abstract class HttpRequestParser {

    private static final byte[] HTTP;
    public static final int HTTP_LENGTH;

    private final int maxParameters;
    private final int maxHeaders;
    private final boolean allowEncodedSlash;
    private final boolean decode;
    private final String charset;

    static {
        try {
            HTTP = "HTTP/1.".getBytes("ASCII");
            HTTP_LENGTH = HTTP.length;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public HttpRequestParser(OptionMap options) {
        maxParameters = options.get(UndertowOptions.MAX_PARAMETERS, 1000);
        maxHeaders = options.get(UndertowOptions.MAX_HEADERS, 200);
        allowEncodedSlash = options.get(UndertowOptions.ALLOW_ENCODED_SLASH, false);
        decode = options.get(UndertowOptions.DECODE_URL, true);
        charset = options.get(UndertowOptions.URL_CHARSET, "UTF-8");
    }

    public static final HttpRequestParser instance(final OptionMap options) {
        try {
            final Class<?> cls = HttpRequestParser.class.getClassLoader().loadClass(HttpRequestParser.class.getName() + "$$generated");

            Constructor<?> ctor = cls.getConstructor(OptionMap.class);
            return (HttpRequestParser) ctor.newInstance(options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public void handle(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder) {
        if (currentState.state == ParseState.VERB) {
            //fast path, we assume that it will parse fully so we avoid all the if statements

            //fast path HTTP GET requests, basically just assume all requests are get
            //and fall out to the state machine if it is not
            final int position = buffer.position();
            if (buffer.remaining() > 3
                    && buffer.get(position) == 'G'
                    && buffer.get(position + 1) == 'E'
                    && buffer.get(position + 2) == 'T') {
                buffer.position(position + 3);
                builder.setRequestMethod(Methods.GET);
            } else {
                handleHttpVerb(buffer, currentState, builder);
            }
            handlePath(buffer, currentState, builder);
            handleHttpVersion(buffer, currentState, builder);
            handleAfterVersion(buffer, currentState);
            while (currentState.state != ParseState.PARSE_COMPLETE && buffer.hasRemaining()) {
                handleHeader(buffer, currentState, builder);
                if (currentState.state == ParseState.HEADER_VALUE) {
                    handleHeaderValue(buffer, currentState, builder);
                }
            }
            return;
        }
        handleStateful(buffer, currentState, builder);
    }

    private void handleStateful(ByteBuffer buffer, ParseState currentState, HttpServerExchange builder) {
        if (currentState.state == ParseState.PATH) {
            handlePath(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }

        if (currentState.state == ParseState.QUERY_PARAMETERS) {
            handleQueryParameters(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }

        if (currentState.state == ParseState.PATH_PARAMETERS) {
            handlePathParameters(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }

        if (currentState.state == ParseState.VERSION) {
            handleHttpVersion(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }
        if (currentState.state == ParseState.AFTER_VERSION) {
            handleAfterVersion(buffer, currentState);
            if (!buffer.hasRemaining()) {
                return;
            }
        }
        while (currentState.state != ParseState.PARSE_COMPLETE) {
            if (currentState.state == ParseState.HEADER) {
                handleHeader(buffer, currentState, builder);
                if (!buffer.hasRemaining()) {
                    return;
                }
            }
            if (currentState.state == ParseState.HEADER_VALUE) {
                handleHeaderValue(buffer, currentState, builder);
                if (!buffer.hasRemaining()) {
                    return;
                }
            }
        }
    }

    protected final int handleHost(ByteBuffer buffer, final HttpServerExchange exchange) {
        if (exchange.getRequestHostName() != null) {
            // already got a host... ignore content
            return 3;
        }
        final int lim = buffer.limit();
        int pos = buffer.position();
        byte b;
        for (;;) {
            if (pos == lim) {
                return 1;
            }
            b = buffer.get(pos++);
            if (b == '\r') {
                // expect LF
                if (pos == lim) {
                    // incomplete; retry
                    return 1;
                }
                b = buffer.get(pos++);
                if (b != '\n') {
                    // malformed request
                    return 2;
                }
                if (pos == lim) {
                    // incomplete; retry
                    return 1;
                }
                b = buffer.get(pos++);
                if (b != ' ' && b != '\t') {
                    // expected whitespace
                    return 2;
                }
                continue;
            } else if (b == ' ' || b == '\t') {
                continue;
            } else {
                break;
            }
        }
        // got a real char, start the real loop
        int start = pos - 1;
        if (pos == lim) {
            return 1;
        }
        b = buffer.get(pos++);
        if (b == '[') {
            // IPv6 style; read until ], then an optional port #
            for (;;) {
                if (pos == lim) {
                    return 1;
                }
                b = buffer.get(pos++);
                if (b == ']') {
                    buffer.position(start);
                    // todo: caching of common host names?
                    exchange.setRequestHostName(new HttpString(buffer, pos - start));
                    // parse port #
                    break;
                } else if (b != ':' && (b < '0' || b > '9') && (b < 'a' || b > 'f') && (b < 'A' || b > 'F')) {
                    return 2;
                }
                // OK
            }
            // expect : for port # or end of field
            if (pos == lim) {
                return 1;
            }
            b = buffer.get(pos++);
            if (b == '\r') {
                // expect \n and end with default port # (0)
                // expect LF
                if (pos == lim) {
                    // incomplete; retry
                    return 1;
                }
                b = buffer.get(pos++);
                if (b != '\n') {
                    // malformed request
                    return 2;
                }
                if (pos == lim) {
                    // incomplete; retry
                    return 1;
                }
                b = buffer.get(pos);
                if (b == ' ' || b == '\t') {
                    // expected end of field
                    return 2;
                }
                buffer.position(pos);
                return 0;
            }
            if (b != ':') {
                return 2;
            }
            // fall out to parse port #
        } else {
            // IPv4 or a host name, then an optional port #
            for (;;) {
                if (pos == lim) {
                    return 1;
                }
                b = buffer.get(pos++);
                if (b != '.' && (b < 'a' || b > 'z') && b != '-' && (b < '0' || b > '9') && (b < 'A' || b > 'Z')) {
                    return 2;
                } else if (b == ':') {
                    buffer.position(start);
                    // todo: caching of common host names?
                    exchange.setRequestHostName(new HttpString(buffer, pos - start));
                    // parse port #
                    break;
                } else if (b == '\r') {
                    // expect end of header
                    buffer.position(start);
                    // todo: caching of common host names?
                    exchange.setRequestHostName(new HttpString(buffer, pos - start));
                    if (pos == lim) {
                        // incomplete; retry
                        return 1;
                    }
                    b = buffer.get(pos++);
                    if (b != '\n') {
                        // malformed request
                        return 2;
                    }
                    if (pos == lim) {
                        // incomplete; retry
                        return 1;
                    }
                    b = buffer.get(pos);
                    if (b == ' ' && b == '\t') {
                        // expected end of field
                        return 2;
                    }
                    buffer.position(pos);
                    return 0;
                }
                // OK
            }
        }
        if (b == '\r') {
            // could be end, could be (invalid!) LWS
            // expect LF
            if (pos == lim) {
                // incomplete; retry
                return 1;
            }
            b = buffer.get(pos++);
            if (b != '\n') {
                // malformed request
                return 2;
            }
            if (pos == lim) {
                // incomplete; retry
                return 1;
            }
            b = buffer.get(pos);
            if (b == ' ' || b == '\t') {
                // *un*expected LWS
                return 2;
            }

            buffer.position(pos);
            // handled!
            return 0;
        }
    }

    abstract void handleHttpVerb(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder);

    abstract void handleHttpVersion(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder);

    abstract void handleHeader(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder);

    /**
     * The parse states for parsing the path.
     */
    private static final int START = 0;
    private static final int FIRST_COLON = 1;
    private static final int FIRST_SLASH = 2;
    private static final int SECOND_SLASH = 3;
    private static final int HOST_DONE = 4;

    /**
     * Parses a path value
     *
     * @param buffer   The buffer
     * @param state    The current state
     * @param exchange The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final void handlePath(ByteBuffer buffer, ParseState state, HttpServerExchange exchange) {
        StringBuilder stringBuilder = state.stringBuilder;
        int parseState = state.parseState;
        int canonicalPathStart = state.pos;
        boolean urlDecodeRequired = state.urlDecodeRequired;

        while (buffer.hasRemaining()) {
            char next = (char) buffer.get();
            if (next == ' ' || next == '\t') {
                if (stringBuilder.length() != 0) {
                    final String path = stringBuilder.toString();
                    final HttpString pathHttpString = new HttpString(path.getBytes(StandardCharsets.UTF_8));
                    if (parseState < HOST_DONE) {
                        String decodedPath = decode(path, urlDecodeRequired, state, allowEncodedSlash);
                        final HttpString decodedPathHttpString = new HttpString(decodedPath.getBytes(StandardCharsets.UTF_8));
                        exchange.setRequestPath(decodedPathHttpString);
                        exchange.setRelativePath(decodedPathHttpString);
                        exchange.setRequestURI(pathHttpString);
                    } else {
                        String thePath = decode(path.substring(canonicalPathStart), urlDecodeRequired, state, allowEncodedSlash);
                        final HttpString thePathHttpString = new HttpString(thePath.getBytes(StandardCharsets.UTF_8));
                        exchange.setRequestPath(thePathHttpString);
                        exchange.setRelativePath(thePathHttpString);
                        exchange.setRequestURI(pathHttpString, true);
                    }
                    exchange.setQueryString(HttpString.EMPTY);
                    state.state = ParseState.VERSION;
                    state.stringBuilder.setLength(0);
                    state.parseState = 0;
                    state.pos = 0;
                    state.urlDecodeRequired = false;
                    return;
                }
            } else if (next == '\r' || next == '\n') {
                throw UndertowMessages.MESSAGES.failedToParsePath();
            } else if (next == '?' && (parseState == START || parseState == HOST_DONE)) {
                final String path = stringBuilder.toString();
                final HttpString pathHttpString = new HttpString(path.getBytes(StandardCharsets.UTF_8));
                if (parseState < HOST_DONE) {
                    String decodedPath = decode(path, urlDecodeRequired, state, allowEncodedSlash);
                    final HttpString decodedPathHttpString = new HttpString(decodedPath.getBytes(StandardCharsets.UTF_8));
                    exchange.setRequestPath(decodedPathHttpString);
                    exchange.setRelativePath(decodedPathHttpString);
                    exchange.setRequestURI(pathHttpString, false);
                } else {
                    String thePath = decode(path.substring(canonicalPathStart), urlDecodeRequired, state, allowEncodedSlash);
                    final HttpString thePathHttpString = new HttpString(thePath.getBytes(StandardCharsets.UTF_8));
                    exchange.setRequestPath(thePathHttpString);
                    exchange.setRelativePath(thePathHttpString);
                    exchange.setRequestURI(pathHttpString, true);
                }
                state.state = ParseState.QUERY_PARAMETERS;
                state.stringBuilder.setLength(0);
                state.parseState = 0;
                state.pos = 0;
                state.urlDecodeRequired = false;
                handleQueryParameters(buffer, state, exchange);
                return;
            } else if (next == ';' && (parseState == START || parseState == HOST_DONE)) {
                final String path = stringBuilder.toString();
                final HttpString pathHttpString = new HttpString(path.getBytes(StandardCharsets.UTF_8));
                if (parseState < HOST_DONE) {
                    String decodedPath = decode(path, urlDecodeRequired, state, allowEncodedSlash);
                    final HttpString decodedPathHttpString = new HttpString(decodedPath.getBytes(StandardCharsets.UTF_8));
                    exchange.setRequestPath(decodedPathHttpString);
                    exchange.setRelativePath(decodedPathHttpString);
                    exchange.setRequestURI(pathHttpString, false);
                } else {
                    String thePath = path.substring(canonicalPathStart);
                    final HttpString thePathHttpString = new HttpString(thePath.getBytes(StandardCharsets.UTF_8));
                    exchange.setRequestPath(thePathHttpString);
                    exchange.setRelativePath(thePathHttpString);
                    exchange.setRequestURI(pathHttpString, true);
                }
                state.state = ParseState.PATH_PARAMETERS;
                state.stringBuilder.setLength(0);
                state.parseState = 0;
                state.pos = 0;
                state.urlDecodeRequired = false;
                handlePathParameters(buffer, state, exchange);
                return;
            } else {

                if (decode && (next == '+' || next == '%')) {
                    urlDecodeRequired = true;
                } else if (next == ':' && parseState == START) {
                    parseState = FIRST_COLON;
                } else if (next == '/' && parseState == FIRST_COLON) {
                    parseState = FIRST_SLASH;
                } else if (next == '/' && parseState == FIRST_SLASH) {
                    parseState = SECOND_SLASH;
                } else if (next == '/' && parseState == SECOND_SLASH) {
                    parseState = HOST_DONE;
                    canonicalPathStart = stringBuilder.length();
                } else if (parseState == FIRST_COLON || parseState == FIRST_SLASH) {
                    parseState = START;
                }
                stringBuilder.append(next);
            }

        }
        state.parseState = parseState;
        state.pos = canonicalPathStart;
        state.urlDecodeRequired = urlDecodeRequired;
    }


    /**
     * Parses a path value
     *
     * @param buffer   The buffer
     * @param state    The current state
     * @param exchange The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final void handleQueryParameters(ByteBuffer buffer, ParseState state, HttpServerExchange exchange) {
        StringBuilder stringBuilder = state.stringBuilder;
        int queryParamPos = state.pos;
        int mapCount = state.mapCount;
        boolean urlDecodeRequired = state.urlDecodeRequired;
        String nextQueryParam = state.nextQueryParam;

        //so this is a bit funky, because it not only deals with parsing, but
        //also deals with URL decoding the query parameters as well, while also
        //maintaining a non-decoded version to use as the query string
        //In most cases these string will be the same, and as we do not want to
        //build up two separate strings we don't use encodedStringBuilder unless
        //we encounter an encoded character

        while (buffer.hasRemaining()) {
            char next = (char) buffer.get();
            if (next == ' ' || next == '\t') {
                final String queryString = stringBuilder.toString();
                exchange.setQueryString(HttpString.fromString(queryString));
                if (nextQueryParam == null) {
                    if (queryParamPos != stringBuilder.length()) {
                        exchange.addQueryParam(decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true), "");
                    }
                } else {
                    exchange.addQueryParam(nextQueryParam, decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true));
                }
                state.state = ParseState.VERSION;
                state.stringBuilder.setLength(0);
                state.pos = 0;
                state.nextQueryParam = null;
                state.urlDecodeRequired = false;
                state.mapCount = 0;
                return;
            } else if (next == '\r' || next == '\n') {
                throw UndertowMessages.MESSAGES.failedToParsePath();
            } else {
                if (decode && (next == '+' || next == '%')) {
                    urlDecodeRequired = true;
                } else if (next == '=' && nextQueryParam == null) {
                    nextQueryParam = decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true);
                    urlDecodeRequired = false;
                    queryParamPos = stringBuilder.length() + 1;
                } else if (next == '&' && nextQueryParam == null) {
                    if (mapCount++ > maxParameters) {
                        throw UndertowMessages.MESSAGES.tooManyQueryParameters(maxParameters);
                    }
                    exchange.addQueryParam(decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true), "");
                    urlDecodeRequired = false;
                    queryParamPos = stringBuilder.length() + 1;
                } else if (next == '&') {
                    if (mapCount++ > maxParameters) {
                        throw UndertowMessages.MESSAGES.tooManyQueryParameters(maxParameters);
                    }
                    exchange.addQueryParam(nextQueryParam, decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true));
                    urlDecodeRequired = false;
                    queryParamPos = stringBuilder.length() + 1;
                    nextQueryParam = null;
                }
                stringBuilder.append(next);

            }

        }
        state.pos = queryParamPos;
        state.nextQueryParam = nextQueryParam;
        state.urlDecodeRequired = urlDecodeRequired;
        state.mapCount = 0;
    }

    private String decode(final String value, boolean urlDecodeRequired, ParseState state, final boolean allowEncodedSlash) {
        if (urlDecodeRequired) {
            return URLUtils.decode(value, charset, allowEncodedSlash, state.decodeBuffer);
        } else {
            return value;
        }
    }


    final void handlePathParameters(ByteBuffer buffer, ParseState state, HttpServerExchange exchange) {
        StringBuilder stringBuilder = state.stringBuilder;
        int queryParamPos = state.pos;
        int mapCount = state.mapCount;
        boolean urlDecodeRequired = state.urlDecodeRequired;
        String nextQueryParam = state.nextQueryParam;

        //so this is a bit funky, because it not only deals with parsing, but
        //also deals with URL decoding the query parameters as well, while also
        //maintaining a non-decoded version to use as the query string
        //In most cases these string will be the same, and as we do not want to
        //build up two separate strings we don't use encodedStringBuilder unless
        //we encounter an encoded character

        while (buffer.hasRemaining()) {
            char next = (char) buffer.get();
            if (next == ' ' || next == '\t' || next == '?') {
                if (nextQueryParam == null) {
                    if (queryParamPos != stringBuilder.length()) {
                        exchange.addPathParam(decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true), "");
                    }
                } else {
                    exchange.addPathParam(nextQueryParam, decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true));
                }
                exchange.setRequestURI(HttpString.fromString(exchange.getRequestURI().toString() + ';' + stringBuilder.toString()), state.parseState > HOST_DONE);
                state.stringBuilder.setLength(0);
                state.pos = 0;
                state.nextQueryParam = null;
                state.mapCount = 0;
                state.urlDecodeRequired = false;
                if (next == '?') {
                    state.state = ParseState.QUERY_PARAMETERS;
                    handleQueryParameters(buffer, state, exchange);
                } else {
                    state.state = ParseState.VERSION;
                }
                return;
            } else if (next == '\r' || next == '\n') {
                throw UndertowMessages.MESSAGES.failedToParsePath();
            } else {
                if (decode && (next == '+' || next == '%')) {
                    urlDecodeRequired = true;
                }
                if (next == '=' && nextQueryParam == null) {
                    nextQueryParam = decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true);
                    urlDecodeRequired = false;
                    queryParamPos = stringBuilder.length() + 1;
                } else if (next == '&' && nextQueryParam == null) {
                    if (mapCount++ > maxParameters) {
                        throw UndertowMessages.MESSAGES.tooManyQueryParameters(maxParameters);
                    }
                    exchange.addPathParam(decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true), "");
                    urlDecodeRequired = false;
                    queryParamPos = stringBuilder.length() + 1;
                } else if (next == '&') {
                    if (mapCount++ > maxParameters) {
                        throw UndertowMessages.MESSAGES.tooManyQueryParameters(maxParameters);
                    }

                    exchange.addPathParam(nextQueryParam, decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true));
                    urlDecodeRequired = false;
                    queryParamPos = stringBuilder.length() + 1;
                    nextQueryParam = null;
                }
                stringBuilder.append(next);

            }

        }
        state.pos = queryParamPos;
        state.nextQueryParam = nextQueryParam;
        state.mapCount = 0;
        state.urlDecodeRequired = urlDecodeRequired;
    }


    /**
     * The parse states for parsing heading values
     */
    private static final int NORMAL = 0;
    private static final int WHITESPACE = 1;
    private static final int BEGIN_LINE_END = 2;
    private static final int LINE_END = 3;
    private static final int AWAIT_DATA_END = 4;

    /**
     * Parses a header value. This is called from the generated  bytecode.
     *
     * @param buffer  The buffer
     * @param state   The current state
     * @param builder The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final void handleHeaderValue(ByteBuffer buffer, ParseState state, HttpServerExchange builder) {
        StringBuilder stringBuilder = state.stringBuilder;

        int parseState = state.parseState;
        while (buffer.hasRemaining() && parseState == NORMAL) {
            final byte next = buffer.get();
            if (next == '\r') {
                parseState = BEGIN_LINE_END;
            } else if (next == '\n') {
                parseState = LINE_END;
            } else if (next == ' ' || next == '\t') {
                parseState = WHITESPACE;
            } else {
                stringBuilder.append((char) next);
            }
        }

        while (buffer.hasRemaining()) {
            final byte next = buffer.get();
            switch (parseState) {
                case NORMAL: {
                    if (next == '\r') {
                        parseState = BEGIN_LINE_END;
                    } else if (next == '\n') {
                        parseState = LINE_END;
                    } else if (next == ' ' || next == '\t') {
                        parseState = WHITESPACE;
                    } else {
                        stringBuilder.append((char) next);
                    }
                    break;
                }
                case WHITESPACE: {
                    if (next == '\r') {
                        parseState = BEGIN_LINE_END;
                    } else if (next == '\n') {
                        parseState = LINE_END;
                    } else if (next == ' ' || next == '\t') {
                    } else {
                        if (stringBuilder.length() > 0) {
                            stringBuilder.append(' ');
                        }
                        stringBuilder.append((char) next);
                        parseState = NORMAL;
                    }
                    break;
                }
                case LINE_END:
                case BEGIN_LINE_END: {
                    if (next == '\n' && parseState == BEGIN_LINE_END) {
                        parseState = LINE_END;
                    } else if (next == '\t' ||
                            next == ' ') {
                        //this is a continuation
                        parseState = WHITESPACE;
                    } else {
                        //we have a header
                        HttpString nextStandardHeader = state.nextHeader;
                        String headerValue = stringBuilder.toString();


                        if (state.mapCount++ > maxHeaders) {
                            throw UndertowMessages.MESSAGES.tooManyHeaders(maxHeaders);
                        }
                        //TODO: we need to decode this according to RFC-2047 if we have seen a =? symbol
                        builder.getRequestHeaders().add(nextStandardHeader, new HttpString(headerValue));

                        state.nextHeader = null;

                        state.leftOver = next;
                        state.stringBuilder.setLength(0);
                        if (next == '\r') {
                            parseState = AWAIT_DATA_END;
                        } else {
                            state.state = ParseState.HEADER;
                            state.parseState = 0;
                            return;
                        }
                    }
                    break;
                }
                case AWAIT_DATA_END: {
                    state.state = ParseState.PARSE_COMPLETE;
                    return;
                }
            }
        }
        //we only write to the state if we did not finish parsing
        state.parseState = parseState;
        return;
    }

    protected void handleAfterVersion(ByteBuffer buffer, ParseState state) {
        boolean newLine = state.leftOver == '\n';
        while (buffer.hasRemaining()) {
            final byte next = buffer.get();
            if (newLine) {
                if (next == '\n') {
                    state.state = ParseState.PARSE_COMPLETE;
                    return;
                } else {
                    state.state = ParseState.HEADER;
                    state.leftOver = next;
                    return;
                }
            } else {
                if (next == '\n') {
                    newLine = true;
                } else if (next != '\r' && next != ' ' && next != '\t') {
                    state.state = ParseState.HEADER;
                    state.leftOver = next;
                    return;
                } else {
                    throw UndertowMessages.MESSAGES.badRequest();
                }
            }
        }
        if (newLine) {
            state.leftOver = '\n';
        }
    }

    /**
     * This is a bit of hack to enable the parser to get access to the HttpString's that are sorted
     * in the static fields of the relevant classes. This means that in most cases a HttpString comparison
     * will take the fast path == route, as they will be the same object
     *
     * @return
     */
    protected static Map<String, HttpString> httpStrings() {
        final Map<String, HttpString> results = new HashMap<String, HttpString>();
        final Class[] classes = {Headers.class, Methods.class, Protocols.class};

        for (Class<?> c : classes) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getType().equals(HttpString.class)) {
                    field.setAccessible(true);
                    HttpString result;
                    try {
                        result = (HttpString) field.get(null);
                        results.put(result.toString(), result);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return results;

    }

}

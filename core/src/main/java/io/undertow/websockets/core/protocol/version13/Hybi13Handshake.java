/*
 * Copyright 2012 JBoss, by Red Hat, Inc
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

package io.undertow.websockets.core.protocol.version13;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;

import io.undertow.websockets.spi.WebSocketHttpExchange;
import io.undertow.util.Headers;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketVersion;
import io.undertow.websockets.core.protocol.version07.Hybi07Handshake;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * The handshaking protocol implementation for Hybi-13.
 *
 * @author Mike Brock
 * @author Stuart Douglas
 */
public class Hybi13Handshake extends Hybi07Handshake {
    public Hybi13Handshake() {
        super(WebSocketVersion.V13, Collections.<String>emptySet(), false);
    }

    public Hybi13Handshake(Set<String> subprotocols, boolean allowExtensions) {
        super(WebSocketVersion.V13, subprotocols, allowExtensions);
    }

    @Override
    protected void handshakeInternal(final WebSocketHttpExchange exchange) {
        String origin = exchange.getRequestHeader(Headers.ORIGIN_STRING);
        if (origin != null) {
            exchange.setResponseHeader(Headers.ORIGIN_STRING, origin);
        }
        String protocol = exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_PROTOCOL_STRING);
        if (protocol != null) {
            exchange.setResponseHeader(Headers.SEC_WEB_SOCKET_PROTOCOL_STRING, protocol);
        }
        exchange.setResponseHeader(Headers.SEC_WEB_SOCKET_LOCATION_STRING, getWebSocketLocation(exchange));

        final String key = exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_KEY_STRING);
        try {
            final String solution = solve(key);
            exchange.setResponseHeader(Headers.SEC_WEB_SOCKET_ACCEPT_STRING, solution);
            performUpgrade(exchange);
        } catch (NoSuchAlgorithmException e) {
            IoUtils.safeClose(exchange);
            exchange.endExchange();
            return;
        }
    }

    @Override
    public WebSocketChannel createChannel(WebSocketHttpExchange exchange, final ConnectedStreamChannel channel, final Pool<ByteBuffer> pool) {
        return new WebSocket13Channel(channel, pool, getWebSocketLocation(exchange), subprotocols, false, allowExtensions);
    }
}

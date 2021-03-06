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

package io.undertow.servlet.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import io.undertow.UndertowMessages;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import org.xnio.IoUtils;

/**
 * A sender that uses an output stream.
 *
 * @author Stuart Douglas
 */
public class BlockingWriterSenderImpl implements Sender {

    public static final int BUFFER_SIZE = 128;

    private final CharsetDecoder charsetDecoder;
    private final HttpServerExchange exchange;
    private final PrintWriter writer;
    private boolean inCall;
    private String next;
    private IoCallback queuedCallback;

    public BlockingWriterSenderImpl(final HttpServerExchange exchange, final PrintWriter writer, final String charset) {
        this.exchange = exchange;
        this.writer = writer;
        this.charsetDecoder = Charset.forName(charset).newDecoder();
    }

    @Override
    public void send(final ByteBuffer buffer, final IoCallback callback) {
        if (inCall) {
            queue(new ByteBuffer[]{buffer}, callback);
            return;
        }
        if (writeBuffer(buffer, callback)) {
            invokeOnComplete(callback);
        }
    }


    @Override
    public void send(final ByteBuffer[] buffer, final IoCallback callback) {
        if (inCall) {
            queue(buffer, callback);
            return;
        }
        for (ByteBuffer b : buffer) {
            if (!writeBuffer(b, callback)) {
                return;
            }
        }
        invokeOnComplete(callback);
    }

    @Override
    public void send(final String data, final IoCallback callback) {
        if (inCall) {
            queue(data, callback);
            return;
        }
        writer.write(data);

        if (writer.checkError()) {
            callback.onException(exchange, this, new IOException());
        } else {
            invokeOnComplete(callback);
        }
    }

    @Override
    public void send(final String data, final Charset charset, final IoCallback callback) {
        if (inCall) {
            queue(new ByteBuffer[]{ByteBuffer.wrap(data.getBytes(charset))}, callback);
            return;
        }
        writer.write(data);
        if (writer.checkError()) {
            callback.onException(exchange, this, new IOException());
        } else {
            invokeOnComplete(callback);
        }
    }

    @Override
    public void close(final IoCallback callback) {
        writer.close();
        invokeOnComplete(callback);
    }

    @Override
    public void close() {
        IoUtils.safeClose(writer);
    }


    private boolean writeBuffer(final ByteBuffer buffer, final IoCallback callback) {
        StringBuilder builder = new StringBuilder();
        try {
            builder.append(charsetDecoder.decode(buffer));
        } catch (CharacterCodingException e) {
            callback.onException(exchange, this, e);
            return false;
        }
        String data = builder.toString();
        writer.write(data);
        if (writer.checkError()) {
            callback.onException(exchange, this, new IOException());
            return false;
        }
        return true;
    }


    private void invokeOnComplete(final IoCallback callback) {
        inCall = true;
        try {
            callback.onComplete(exchange, this);
        } finally {
            inCall = false;
        }
        while (next != null) {
            String next = this.next;
            IoCallback queuedCallback = this.queuedCallback;
            this.next = null;
            this.queuedCallback = null;
            writer.write(next);
            if (writer.checkError()) {
                queuedCallback.onException(exchange, this, new IOException());
            } else {
                inCall = true;
                try {
                    queuedCallback.onComplete(exchange, this);
                } finally {
                    inCall = false;
                }
            }
        }
    }

    private void queue(final ByteBuffer[] byteBuffers, final IoCallback ioCallback) {
        //if data is sent from withing the callback we queue it, to prevent the stack growing indefinitly
        if (next != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        StringBuilder builder = new StringBuilder();
        for (ByteBuffer buffer : byteBuffers) {
            try {
                builder.append(charsetDecoder.decode(buffer));
            } catch (CharacterCodingException e) {
                ioCallback.onException(exchange, this, e);
                return;
            }
        }
        this.next = builder.toString();
        queuedCallback = ioCallback;
    }

    private void queue(final String data, final IoCallback callback) {
        //if data is sent from withing the callback we queue it, to prevent the stack growing indefinitly
        if (next != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        next = data;
        queuedCallback = callback;
    }

}

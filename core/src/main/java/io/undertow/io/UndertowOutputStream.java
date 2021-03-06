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

package io.undertow.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.xnio.Buffers;
import org.xnio.Pooled;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;

import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * Buffering output stream that wraps a channel.
 * <p/>
 * This stream delays channel creation, so if a response will fit in the buffer it is not nessesary to
 * set the content length header.
 *
 * @author Stuart Douglas
 */
public class UndertowOutputStream extends OutputStream implements BufferWritableOutputStream {

    private final HttpServerExchange exchange;
    private ByteBuffer buffer;
    private Pooled<ByteBuffer> pooledBuffer;
    private StreamSinkChannel channel;
    private int state;
    private int written;
    private final Integer contentLength;

    private static final int FLAG_CLOSED = 1;
    private static final int FLAG_WRITE_STARTED = 1 << 1;

    /**
     * Construct a new instance.  No write timeout is configured.
     *
     * @param exchange
     */
    public UndertowOutputStream(HttpServerExchange exchange) {
        this.exchange = exchange;
        final String cl = exchange.getResponseHeaders().getFirst(Headers.CONTENT_LENGTH);
        if (cl != null) {
            contentLength = Integer.parseInt(cl);
        } else {
            contentLength = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void write(final int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    /**
     * {@inheritDoc}
     */
    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * {@inheritDoc}
     */
    public void write(final byte[] b, final int off, final int len) throws IOException {
        if (len < 1) {
            return;
        }
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        int written = 0;
        ByteBuffer buffer = buffer();
        while (written < len) {
            if (buffer.remaining() >= (len - written)) {
                buffer.put(b, off + written, len - written);
                if (buffer.remaining() == 0) {
                    writeBuffer();
                }
                updateWritten(len);
                return;
            } else {
                int remaining = buffer.remaining();
                buffer.put(b, off + written, remaining);
                writeBuffer();
                written += remaining;
            }
        }
        updateWritten(len);
    }


    @Override
    public void write(ByteBuffer[] buffers) throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        int len = 0;
        for (ByteBuffer buf : buffers) {
            len += buf.remaining();
        }
        if (len < 1) {
            return;
        }

        //if we have received the exact amount of content write it out in one go
        //this is a common case when writing directly from a buffer cache.
        if (this.written == 0 && contentLength != null && len == contentLength) {
            if (channel == null) {
                channel = exchange.getResponseChannel();
            }
            Channels.writeBlocking(channel, buffers, 0, buffers.length);
            state |= FLAG_WRITE_STARTED;
        } else {
            ByteBuffer buffer = buffer();
            if (len < buffer.remaining()) {
                Buffers.copy(buffer, buffers, 0, buffers.length);
            } else {
                if (channel == null) {
                    channel = exchange.getResponseChannel();
                }
                if (buffer.position() == 0) {
                    Channels.writeBlocking(channel, buffers, 0, buffers.length);
                } else {
                    final ByteBuffer[] newBuffers = new ByteBuffer[buffers.length + 1];
                    buffer.flip();
                    newBuffers[0] = buffer;
                    System.arraycopy(buffers, 0, newBuffers, 1, buffers.length);
                    Channels.writeBlocking(channel, newBuffers, 0, newBuffers.length);
                    buffer.clear();
                }
                state |= FLAG_WRITE_STARTED;
            }
        }
        updateWritten(len);
    }

    @Override
    public void write(ByteBuffer byteBuffer) throws IOException {
        write(new ByteBuffer[]{byteBuffer});
    }

    void updateWritten(final int len) throws IOException {
        this.written += len;
        if (contentLength != null && this.written >= contentLength) {
            flush();
            close();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void flush() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        if (buffer != null && buffer.position() != 0) {
            writeBuffer();
        }
        if (channel == null) {
            channel = exchange.getResponseChannel();
        }
        Channels.flushBlocking(channel);
    }

    private void writeBuffer() throws IOException {
        buffer.flip();
        if (channel == null) {
            channel = exchange.getResponseChannel();
        }
        Channels.writeBlocking(channel, buffer);
        buffer.clear();
        state |= FLAG_WRITE_STARTED;
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) return;
        try {
            state |= FLAG_CLOSED;
            if (anyAreClear(state, FLAG_WRITE_STARTED) && channel == null) {
                if (buffer == null) {
                    exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "0");
                } else {
                    exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "" + buffer.position());
                }
            }
            if (buffer != null) {
                writeBuffer();
            }
            if (channel == null) {
                channel = exchange.getResponseChannel();
            }
            StreamSinkChannel channel = this.channel;
            channel.shutdownWrites();
            Channels.flushBlocking(channel);
        } finally {
            if (pooledBuffer != null) {
                pooledBuffer.free();
                buffer = null;
            } else {
                buffer = null;
            }
        }
    }

    private ByteBuffer buffer() {
        ByteBuffer buffer = this.buffer;
        if (buffer != null) {
            return buffer;
        }
        this.pooledBuffer = exchange.getConnection().getBufferPool().allocate();
        this.buffer = pooledBuffer.getResource();
        return this.buffer;
    }

}

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

package io.undertow.server.handlers.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.FileAccess;
import org.xnio.IoUtils;
import org.xnio.channels.ChannelFactory;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.SuspendableWriteChannel;

/**
 * A file cache that serves files directly with no caching using non-blocking I/O (may block on file access).
 *
 * @author Stuart Douglas
 */
public class InLineFileCache implements FileCache {

    public static final FileCache INSTANCE = new InLineFileCache();

    @Override
    public void serveFile(final HttpServerExchange exchange, final File file, final boolean directoryListingEnabled) {
        // ignore request body
        IoUtils.safeShutdownReads(exchange.getRequestChannel());
        final HttpString method = exchange.getRequestMethod();
        final FileChannel fileChannel;
        long length;
        try {
            try {
                fileChannel = exchange.getConnection().getWorker().getXnio().openFile(file, FileAccess.READ_ONLY);
            } catch (FileNotFoundException e) {
                exchange.setResponseCode(404);
                return;
            }
            length = fileChannel.size();
        } catch (IOException e) {
            UndertowLogger.REQUEST_LOGGER.exceptionReadingFile(file, e);
            exchange.setResponseCode(500);
            return;
        }
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, Long.toString(length));
        if (method.equals(Methods.HEAD)) {
            return;
        }
        if (! method.equals(Methods.GET)) {
            exchange.setResponseCode(500);
            return;
        }
        final ChannelFactory<StreamSinkChannel> factory = exchange.getResponseChannelFactory();
        if (factory == null) {
            IoUtils.safeClose(fileChannel);
            return;
        }
        final StreamSinkChannel responseChannel = factory.create();
        responseChannel.getCloseSetter().set(new ChannelListener<Channel>() {
            public void handleEvent(final Channel channel) {
                IoUtils.safeClose(fileChannel);
            }
        });
        long pos = 0L;
        long res;
        while (length > 0L) {
            try {
                res = responseChannel.transferFrom(fileChannel, pos, length);
            } catch (IOException e) {
                IoUtils.safeClose(fileChannel);
                IoUtils.safeClose(responseChannel);
                return;
            }
            if (res == 0L) {
                responseChannel.getWriteSetter().set(new TransferListener(length, pos, responseChannel, fileChannel));
                responseChannel.resumeWrites();
                return;
            }
            pos += res;
            length -= res;
        }
        IoUtils.safeClose(fileChannel);
        HttpHandlers.closeAndFlush(responseChannel);
    }

    private static class TransferListener implements ChannelListener<StreamSinkChannel> {

        private long length;
        private long pos;
        private final StreamSinkChannel responseChannel;
        private final FileChannel fileChannel;

        public TransferListener(final long length, final long pos, final StreamSinkChannel responseChannel, final FileChannel fileChannel) {
            this.length = length;
            this.pos = pos;
            this.responseChannel = responseChannel;
            this.fileChannel = fileChannel;
        }

        public void handleEvent(final StreamSinkChannel channel) {
            long res;
            long length = this.length;
            long pos = this.pos;
            try {
                while (length > 0L) {
                    try {
                        res = responseChannel.transferFrom(fileChannel, pos, length);
                    } catch (IOException e) {
                        IoUtils.safeClose(fileChannel);
                        responseChannel.suspendWrites();
                        IoUtils.safeClose(responseChannel);
                        return;
                    }
                    if (res == 0L) {
                        return;
                    }
                    pos += res;
                    length -= res;
                }
                IoUtils.safeClose(fileChannel);
                HttpHandlers.closeAndFlush(responseChannel);
            } finally {
                this.length = length;
                this.pos = pos;
            }
        }
    }
}

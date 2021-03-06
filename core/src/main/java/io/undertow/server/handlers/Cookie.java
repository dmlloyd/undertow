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

package io.undertow.server.handlers;

import java.util.Date;
import java.util.Map;

import io.undertow.util.AttachmentKey;
import io.undertow.util.AttachmentList;

/**
 * A HTTP cookie.
 *
 * @see CookieHandler
 * @author Stuart Douglas
 */
public interface Cookie {

    AttachmentKey<Map<String, Cookie>> REQUEST_COOKIES = AttachmentKey.create(Map.class);
    AttachmentKey<AttachmentList<Cookie>> RESPONSE_COOKIES = AttachmentKey.createList(Cookie.class);

    String getName();

    String getValue();

    Cookie setValue(final String value);

    String getPath();

    Cookie setPath(final String path);

    String getDomain();

    Cookie setDomain(final String domain);

    Integer getMaxAge();

    Cookie setMaxAge(final Integer maxAge);

    boolean isDiscard();

    Cookie setDiscard(final boolean discard);

    boolean isSecure();

    Cookie setSecure(final boolean secure);

    int getVersion();

    Cookie setVersion(final int version);

    boolean isHttpOnly();

    Cookie setHttpOnly(final boolean httpOnly);

    Date getExpires();

    Cookie setExpires(final Date expires);

    String getComment();

    Cookie setComment(final String comment);
}

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

import static io.undertow.annotationprocessor.CaseSensitivity.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@Retention(RetentionPolicy.SOURCE)
@Target({})
public @interface HttpHeaderConfig {
    String name();
    boolean fast() default false;
    boolean singleton() default false;
    boolean csv() default false;
    CaseSensitivity caseSensitive() default CASE_SENSITIVE;

    /**
     * The method to handle this header.
     * <p>
     * The method must be one of:
     * <ul>
     *     <li>{@code (ByteBuffer, &lt;&lt;EXCHANGE TYPE&gt;&gt;)}, returning:<ul>
     *         <li>0 if parsing is OK (buffer position on next item)</li>
     *         <li>1 if not enough data was read to finish parsing (buffer position unmodified)</li>
     *         <li>2 if the request should be rejected with a 400 status</li>
     *         <li>3 if the remaining header value content should be consumed and discarded</li>
     *     </ul></li>
     *     <li>{@code (HttpString, &lt;&lt;EXCHANGE TYPE&gt;&gt;)} returning {@code void}</li>
     *     <li>{@code (HttpString, int, &lt;&lt;EXCHANGE TYPE&gt;&gt;)} (host name and optional port, possibly 0) returning {@code void}</li>
     *     <li>{@code (int, &lt;&lt;EXCHANGE TYPE&gt;&gt;)} returning {@code void}</li>
     *     <li>{@code (long, &lt;&lt;EXCHANGE TYPE&gt;&gt;)} returning {@code void}</li>
     * </ul>
     * <ul>
     * </ul>
     *
     * @return the method name
     */
    String method() default "NONE";
    HeaderType headerType() default HeaderType.HTTP_STRING;
    HttpHeaderValueConfig[] values() default {};
}

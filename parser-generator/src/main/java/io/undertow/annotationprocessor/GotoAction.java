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

import org.jboss.classfilewriter.code.CodeAttribute;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class GotoAction extends Action {
    private final CodeMarker target;

    public GotoAction(final CodeMarker target) {
        this.target = target;
    }

    protected void emitAction(final CodeAttribute codeAttribute) {
        target.gotoFrom(codeAttribute);
    }

    void emitIfEQ(final CodeAttribute codeAttribute) {
        target.ifEQFrom(codeAttribute);
    }

    void emitIfNE(final CodeAttribute codeAttribute) {
        target.ifNEFrom(codeAttribute);
    }

    void emitIfICmpEQ(final CodeAttribute codeAttribute) {
        target.ifICmpEQFrom(codeAttribute);
    }

    void emitIfICmpNE(final CodeAttribute codeAttribute) {
        target.ifICmpNEFrom(codeAttribute);
    }

    void emitIfICmpLE(final CodeAttribute codeAttribute) {
        target.ifICmpLEFrom(codeAttribute);
    }
}

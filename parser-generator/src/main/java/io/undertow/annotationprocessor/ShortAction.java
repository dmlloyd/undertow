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

import org.jboss.classfilewriter.code.BranchEnd;
import org.jboss.classfilewriter.code.CodeAttribute;

/**
 * Short actions invert the condition and skip over the action body if true.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class ShortAction extends Action {

    void emitIfEQ(final CodeAttribute codeAttribute) {
        final BranchEnd end = codeAttribute.ifne();
        emitAction(codeAttribute);
        codeAttribute.branchEnd(end);
    }

    void emitIfNE(final CodeAttribute codeAttribute) {
        final BranchEnd end = codeAttribute.ifeq();
        emitAction(codeAttribute);
        codeAttribute.branchEnd(end);
    }

    void emitIfICmpEQ(final CodeAttribute codeAttribute) {
        final BranchEnd end = codeAttribute.ifIcmpne();
        emitAction(codeAttribute);
        codeAttribute.branchEnd(end);
    }

    void emitIfICmpNE(final CodeAttribute codeAttribute) {
        final BranchEnd end = codeAttribute.ifIcmpeq();
        emitAction(codeAttribute);
        codeAttribute.branchEnd(end);
    }

    void emitIfICmpLE(final CodeAttribute codeAttribute) {
        final BranchEnd end = codeAttribute.ifIcmpgt();
        emitAction(codeAttribute);
        codeAttribute.branchEnd(end);
    }
}

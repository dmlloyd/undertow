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

import java.util.Comparator;
import java.util.TreeMap;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class MatchRule {
    private final String text;
    private final Action action;

    public MatchRule(final String text, final Action action) {
        this.text = text;
        this.action = action;
    }

    void addToState(Messager messager, Element element, TreeMap<Byte, StateMachine.State> map, Comparator<Byte> comparator) {
        StateMachine.State nextState = null;
        Byte b;
        for (char c : text.toCharArray()) {
            b = Byte.valueOf((byte) c);
            nextState = map.get(b);
            if (nextState == null) {
                map.put(b, nextState = new StateMachine.State(b.byteValue(), comparator));
            }
            map = nextState.next;
        }
        if (nextState == null) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Empty match for '" + text + "'", element);
            return;
        }
        if (nextState.action != null) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Duplicate string match for '" + text + "'", element);
            return;
        }
        nextState.action = action;
    }

    public String getText() {
        return text;
    }

    public Action getAction() {
        return action;
    }
}

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

package io.undertow.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HeaderValuesTestCase {

    @Test
    public void testBasic() {
        final HeaderValues headerValues = new HeaderValues(Headers.DEFLATE);
        assertEquals(0, headerValues.size());
        assertTrue(headerValues.isEmpty());
        assertFalse(headerValues.iterator().hasNext());
        assertFalse(headerValues.descendingIterator().hasNext());
        assertFalse(headerValues.listIterator().hasNext());
        assertFalse(headerValues.listIterator(0).hasNext());
        assertNull(headerValues.peek());
        assertNull(headerValues.peekFirst());
        assertNull(headerValues.peekLast());
    }

    @Test
    public void testAdd() {
        HeaderValues headerValues = new HeaderValues(Headers.HOST);
        assertTrue(headerValues.add(new HttpString("Foo")));
        assertTrue(headerValues.contains(new HttpString("Foo")));
        assertFalse(headerValues.contains(new HttpString("Bar")));
        assertFalse(headerValues.isEmpty());
        assertEquals(1, headerValues.size());
        assertEquals("Foo", headerValues.peek().toString());
        assertEquals("Foo", headerValues.peekFirst().toString());
        assertEquals("Foo", headerValues.peekLast().toString());
        assertEquals("Foo", headerValues.get(0).toString());

        assertTrue(headerValues.offerFirst(new HttpString("First!")));
        assertTrue(headerValues.contains(new HttpString("First!")));
        assertTrue(headerValues.contains(new HttpString("Foo")));
        assertEquals(2, headerValues.size());
        assertEquals("First!", headerValues.peek().toString());
        assertEquals("First!", headerValues.peekFirst().toString());
        assertEquals("First!", headerValues.get(0).toString());
        assertEquals("Foo", headerValues.peekLast().toString());
        assertEquals("Foo", headerValues.get(1).toString());

        assertTrue(headerValues.offerLast(new HttpString("Last!")));
        assertTrue(headerValues.contains(new HttpString("First!")));
        assertTrue(headerValues.contains(new HttpString("Foo")));
        assertTrue(headerValues.contains(new HttpString("First!")));
        assertEquals(3, headerValues.size());
        assertEquals("First!", headerValues.peek().toString());
        assertEquals("First!", headerValues.peekFirst().toString());
        assertEquals("First!", headerValues.get(0).toString());
        assertEquals("Foo", headerValues.get(1).toString());
        assertEquals("Last!", headerValues.peekLast().toString());
        assertEquals("Last!", headerValues.get(2).toString());
    }

}

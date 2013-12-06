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

import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

/**
 * An array-backed list/deque for header string values.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HeaderValues extends AbstractCollection<HttpString> implements Deque<HttpString>, List<HttpString>, RandomAccess {

    private static final HttpString[] NO_STRINGS = new HttpString[0];
    final HttpString key;
    byte head, size;
    Object value;
    AsStrings asStrings;

    HeaderValues(final HttpString key) {
        this.key = key;
    }

    public HttpString getHeaderName() {
        return key;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        final byte size = this.size;
        if (size == 0) return;
        final byte head = this.head;
        final Object value = this.value;
        if (value instanceof HttpString[]) {
            final HttpString[] strings = (HttpString[]) value;
            final int len = strings.length;
            final int tail = head + size;
            if (tail > len) {
                Arrays.fill(strings, head, len, null);
                Arrays.fill(strings, 0, tail - len, null);
            } else {
                Arrays.fill(strings, head, tail, null);
            }
        } else {
            this.value = null;
        }
        this.head = this.size = 0;
    }

    private int index(int idx) {
        assert idx >= 0;
        assert idx < size;
        idx += head;
        final int len = ((HttpString[]) value).length;
        if (idx > len) {
            idx -= len;
        }
        return idx;
    }

    public ListIterator<HttpString> listIterator() {
        return iterator(0, true);
    }

    public ListIterator<HttpString> listIterator(final int index) {
        return iterator(index, true);
    }

    public Iterator<HttpString> iterator() {
        return iterator(0, true);
    }

    public Iterator<HttpString> descendingIterator() {
        return iterator(0, false);
    }

    ListIterator<HttpString> iterator(final int start, final boolean forwards) {
        return new ListIterator<HttpString>() {
            int idx = start;
            int returned = -1;

            public boolean hasNext() {
                return idx < size;
            }

            public boolean hasPrevious() {
                return idx > 0;
            }

            public HttpString next() {
                try {
                    final HttpString next;
                    if (forwards) {
                        int idx = this.idx;
                        next = get(idx);
                        returned = idx;
                        this.idx = idx + 1;
                        return next;
                    } else {
                        int idx = this.idx - 1;
                        next = get(idx);
                        this.idx = returned = idx;
                    }
                    return next;
                } catch (IndexOutOfBoundsException e) {
                    throw new NoSuchElementException();
                }
            }

            public int nextIndex() {
                return idx;
            }

            public HttpString previous() {
                try {
                    final HttpString prev;
                    if (forwards) {
                        int idx = this.idx - 1;
                        prev = get(idx);
                        this.idx = returned = idx;
                    } else {
                        int idx = this.idx;
                        prev = get(idx);
                        returned = idx;
                        this.idx = idx + 1;
                        return prev;
                    }
                    return prev;
                } catch (IndexOutOfBoundsException e) {
                    throw new NoSuchElementException();
                }
            }

            public int previousIndex() {
                return idx - 1;
            }

            public void remove() {
                if (returned == -1) {
                    throw new IllegalStateException();
                }
                HeaderValues.this.remove(returned);
                returned = -1;
            }

            public void set(final HttpString headerValue) {
                if (returned == -1) {
                    throw new IllegalStateException();
                }
                HeaderValues.this.set(returned, headerValue);
            }

            public void add(final HttpString headerValue) {
                if (returned == -1) {
                    throw new IllegalStateException();
                }
                final int idx = this.idx;
                HeaderValues.this.add(idx, headerValue);
                this.idx = idx + 1;
                returned = -1;
            }
        };
    }

    public boolean offerFirst(final HttpString headerValue) {
        int size = this.size;
        if (headerValue == null || size == Byte.MAX_VALUE) return false;
        final Object value = this.value;
        if (value instanceof HttpString[]) {
            final HttpString[] strings = (HttpString[]) value;
            final int len = strings.length;
            final byte head = this.head;
            if (size == len) {
                final HttpString[] newStrings = Arrays.copyOfRange(strings, head, head + len + (len << 1));
                final int end = head + size;
                if (end > len) {
                    System.arraycopy(strings, 0, newStrings, len - head, end - len);
                }
                newStrings[this.head = (byte) (head - 1)] = headerValue;
                this.value = newStrings;
            } else if (head == 0) {
                strings[this.head = (byte) (len - 1)] = headerValue;
            } else {
                strings[this.head = (byte) (head - 1)] = headerValue;
            }
            this.size = (byte) (size + 1);
        } else {
            if (size == 0) {
                this.value = headerValue;
                this.size = (byte) 1;
            } else {
                this.value = new HttpString[] { headerValue, (HttpString) value, null, null };
                this.size = (byte) 2;
            }
            this.head = 0;
        }
        return true;
    }

    public boolean offerLast(final HttpString headerValue) {
        int size = this.size;
        if (headerValue == null || size == Byte.MAX_VALUE) return false;
        final Object value = this.value;
        if (value instanceof HttpString[]) {
            final HttpString[] strings = (HttpString[]) value;
            final int len = strings.length;
            final byte head = this.head;
            final int end = head + size;
            if (size == len) {
                final HttpString[] newStrings = Arrays.copyOfRange(strings, head, head + len + (len << 1));
                if (end > len) {
                    System.arraycopy(strings, 0, newStrings, len - head, end - len);
                }
                newStrings[len] = headerValue;
                this.value = newStrings;
            } else if (end >= len) {
                strings[end - len] = headerValue;
            } else {
                strings[end] = headerValue;
            }
            this.size = (byte) (size + 1);
        } else {
            if (size == 0) {
                this.value = headerValue;
                this.size = (byte) 1;
            } else {
                this.value = new HttpString[] { (HttpString) value, headerValue, null, null };
                this.size = (byte) 2;
            }
            this.head = 0;
        }
        return true;
    }

    private boolean offer(int idx, final HttpString headerValue) {
        int size = this.size;
        if (idx < 0 || idx > size || size == Byte.MAX_VALUE || headerValue == null) return false;
        if (idx == 0) return offerFirst(headerValue);
        if (idx == size) return offerLast(headerValue);
        assert size >= 2; // must be >= 2 to pass the last two checks
        final Object value = this.value;
        assert value instanceof HttpString[];
        final HttpString[] strings = (HttpString[]) value;
        final int len = strings.length;
        final byte head = this.head;
        final int end = head + size;
        final int headIdx = head + idx;
        // This stuff is all algebraically derived.
        if (size == len) {
            // Grow the list, copy each segment into new spots so that head = 0
            final int newLen = (len << 1) + len;
            final HttpString[] newStrings = new HttpString[newLen];
            if (head == 0) {
                assert headIdx == len;
                assert end == len;
                System.arraycopy(value, 0, newStrings, 0, idx);
                System.arraycopy(value, idx, newStrings, idx + 1, len - idx);
            } else if (headIdx < len) {
                System.arraycopy(value, head, newStrings, 0, idx);
                System.arraycopy(value, headIdx, newStrings, idx + 1, len - headIdx);
                System.arraycopy(value, 0, newStrings, len - head + 1, head);
            } else if (headIdx > len) {
                System.arraycopy(value, 0, newStrings, len - head, headIdx - len);
                System.arraycopy(value, headIdx - len, newStrings, idx + 1, len - idx + 1);
                System.arraycopy(value, head, newStrings, 0, len - head);
            }
            // finally fill in the new value
            newStrings[idx] = headerValue;
            this.value = newStrings;
            this.head = 0;
        } else if (end > len) {
            if (headIdx < len) {
                System.arraycopy(value, head, value, head - 1, idx);
                strings[headIdx - 1] = headerValue;
                this.head = (byte) (head - 1);
            } else if (headIdx > len) {
                System.arraycopy(value, headIdx - len, value, headIdx - len + 1, size - idx);
                strings[headIdx - len] = headerValue;
            } else {
                assert headIdx == len;
                System.arraycopy(value, 0, value, 1, end - len);
                strings[0] = headerValue;
            }
            strings[idx] = headerValue;
        } else {
            assert size < len && end <= len;
            if (head == 0 || idx >= size >> 1) {
                assert end < len;
                System.arraycopy(value, headIdx, value, headIdx + 1, size - idx);
                strings[headIdx] = headerValue;
            } else {
                assert end <= len || idx < size << 1;
                assert head > 0;
                System.arraycopy(value, headIdx, value, headIdx - 1, size - idx);
                strings[headIdx - 1] = headerValue;
                this.head = (byte) (head - 1);
            }
        }
        this.size = (byte) (size + 1);
        return true;
    }

    public HttpString pollFirst() {
        final byte size = this.size;
        if (size == 0) return null;

        final Object value = this.value;
        if (value instanceof HttpString) {
            this.size = 0;
            this.value = null;
            return (HttpString) value;
        } else {
            final HttpString[] strings = (HttpString[]) value;
            int idx = head++;
            this.size = (byte) (size - 1);
            final int len = strings.length;
            if (idx > len) idx -= len;
            try {
                return strings[idx];
            } finally {
                strings[idx] = null;
            }
        }
    }

    public HttpString pollLast() {
        final byte size = this.size;
        if (size == 0) return null;

        final Object value = this.value;
        if (value instanceof HttpString) {
            this.size = 0;
            this.value = null;
            return (HttpString) value;
        } else {
            final HttpString[] strings = (HttpString[]) value;
            int idx = head + (this.size = (byte) (size - 1));
            final int len = strings.length;
            if (idx > len) idx -= len;
            try {
                return strings[idx];
            } finally {
                strings[idx] = null;
            }
        }
    }

    public HttpString remove(int idx) {
        final int size = this.size;
        if (idx < 0 || idx >= size) throw new IndexOutOfBoundsException();
        if (idx == 0) return removeFirst();
        if (idx == size - 1) return removeLast();
        assert size > 2; // must be > 2 to pass the last two checks
        // value must be an array since size > 2
        final HttpString[] value = (HttpString[]) this.value;
        final int len = value.length;
        final byte head = this.head;
        final int headIdx = idx + head;
        final int end = head + size;
        if (end > len) {
            if (headIdx > len) {
                try {
                    return value[headIdx - len];
                } finally {
                    System.arraycopy(value, headIdx + 1 - len, value, headIdx - len, size - idx - 1);
                    this.size = (byte) (size - 1);
                }
            } else {
                try {
                    return value[headIdx];
                } finally {
                    System.arraycopy(value, head, value, head + 1, idx);
                    this.size = (byte) (size - 1);
                }
            }
        } else {
            try {
                return value[headIdx];
            } finally {
                System.arraycopy(value, headIdx + 1, value, headIdx, size - idx - 1);
                this.size = (byte) (size - 1);
            }
        }
    }

    public HttpString get(int idx) {
        if (idx > size) {
            throw new IndexOutOfBoundsException();
        }
        Object value = this.value;
        assert value != null;
        if (value instanceof HttpString) {
            assert size == 1;
            return (HttpString) value;
        }
        final HttpString[] a = (HttpString[]) value;
        return a[index(idx)];
    }

    public int indexOf(final Object o) {
        if (o == null || size == 0) return -1;
        if (value instanceof HttpString[]) {
            final HttpString[] list = (HttpString[]) value;
            final int len = list.length;
            int idx;
            for (int i = 0; i < size; i ++) {
                idx = i + head;
                if ((idx > len ? list[idx - len] : list[idx]).equals(o)) {
                    return i;
                }
            }
        } else if (o.equals(value)) {
            return 0;
        }
        return -1;
    }

    public int lastIndexOf(final Object o) {
        if (o == null || size == 0) return -1;
        if (value instanceof HttpString[]) {
            final HttpString[] list = (HttpString[]) value;
            final int len = list.length;
            int idx;
            for (int i = size - 1; i >= 0; i --) {
                idx = i + head;
                if ((idx > len ? list[idx - len] : list[idx]).equals(o)) {
                    return i;
                }
            }
        } else if (o.equals(value)) {
            return 0;
        }
        return -1;
    }

    public HttpString set(final int index, final HttpString element) {
        if (element == null) throw new IllegalArgumentException();

        final byte size = this.size;
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();

        final Object value = this.value;
        if (size == 1 && value instanceof HttpString) try {
            return (HttpString) value;
        } finally {
            this.value = element;
        } else {
            final HttpString[] list = (HttpString[]) value;
            final int i = index(index);
            try {
                return list[i];
            } finally {
                list[i] = element;
            }
        }
    }

    public boolean addAll(int index, final Collection<? extends HttpString> c) {
        final int size = this.size;
        if (index < 0 || index > size) throw new IndexOutOfBoundsException();
        final Iterator<? extends HttpString> iterator = c.iterator();
        boolean result = false;
        while (iterator.hasNext()) { result |= offer(index, iterator.next()); }
        return result;
    }

    public List<HttpString> subList(final int fromIndex, final int toIndex) {
        // todo - this is about 75% correct, by spec...
        if (fromIndex < 0 || toIndex > size || fromIndex > toIndex) throw new IndexOutOfBoundsException();
        final int len = toIndex - fromIndex;
        final HttpString[] strings = new HttpString[len];
        for (int i = 0; i < len; i ++) {
            strings[i] = get(i + fromIndex);
        }
        return Arrays.asList(strings);
    }

    public HttpString[] toArray() {
        int size = this.size;
        if (size == 0) { return NO_STRINGS; }
        final Object v = this.value;
        if (v instanceof HttpString) return new HttpString[] { (HttpString) v };
        final HttpString[] list = (HttpString[]) v;
        final int head = this.head;
        final int len = list.length;
        final int copyEnd = head + size;
        if (copyEnd < len) {
            return Arrays.copyOfRange(list, head, copyEnd);
        } else {
            HttpString[] ret = Arrays.copyOfRange(list, head, copyEnd);
            System.arraycopy(list, 0, ret, len - head, copyEnd - len);
            return ret;
        }
    }

    public <T> T[] toArray(final T[] a) {
        int size = this.size;
        if (size == 0) return a;
        final int inLen = a.length;
        final Object[] target = inLen < size ? Arrays.copyOfRange(a, inLen, inLen + size) : a;
        final Object v = this.value;
        if (v instanceof HttpString) {
            target[0] = (T)v;
        } else {
            final HttpString[] list = (HttpString[]) v;
            final int head = this.head;
            final int len = list.length;
            final int copyEnd = head + size;
            if (copyEnd < len) {
                System.arraycopy(list, head, target, 0, size);
            } else {
                final int wrapEnd = len - head;
                System.arraycopy(list, head, target, 0, wrapEnd);
                System.arraycopy(list, 0, target, wrapEnd, copyEnd - len);
            }
        }
        return (T[]) target;
    }

    //======================================
    //
    // Derived methods
    //
    //======================================

    public void addFirst(final HttpString s) {
        if (s == null) return;
        if (! offerFirst(s)) throw new IllegalStateException();
    }

    public void addLast(final HttpString s) {
        if (s == null) return;
        if (! offerLast(s)) throw new IllegalStateException();
    }

    public void add(final int index, final HttpString s) {
        if (s == null) return;
        if (! offer(index, s)) throw new IllegalStateException();
    }

    public boolean contains(final Object o) {
        return indexOf(o) != -1;
    }

    public HttpString peekFirst() {
        return size == 0 ? null : get(0);
    }

    public HttpString peekLast() {
        return size == 0 ? null : get(size - 1);
    }

    public boolean removeFirstOccurrence(final Object o) {
        int i = indexOf(o);
        return i != -1 && remove(i) != null;
    }

    public boolean removeLastOccurrence(final Object o) {
        int i = lastIndexOf(o);
        return i != -1 && remove(i) != null;
    }

    public boolean add(final HttpString s) {
        addLast(s);
        return true;
    }

    public void push(final HttpString s) {
        addFirst(s);
    }

    public HttpString pop() {
        return removeFirst();
    }

    public boolean offer(final HttpString s) {
        return offerLast(s);
    }

    public HttpString poll() {
        return pollFirst();
    }

    public HttpString peek() {
        return peekFirst();
    }

    public HttpString remove() {
        return removeFirst();
    }

    public HttpString removeFirst() {
        final HttpString s = pollFirst();
        if (s == null) {
            throw new NoSuchElementException();
        }
        return s;
    }

    public HttpString removeLast() {
        final HttpString s = pollLast();
        if (s == null) {
            throw new NoSuchElementException();
        }
        return s;
    }

    public HttpString getFirst() {
        final HttpString s = peekFirst();
        if (s == null) {
            throw new NoSuchElementException();
        }
        return s;
    }

    public HttpString getLast() {
        final HttpString s = peekLast();
        if (s == null) {
            throw new NoSuchElementException();
        }
        return s;
    }

    public HttpString element() {
        return getFirst();
    }

    public boolean remove(Object obj) {
        return removeFirstOccurrence(obj);
    }

    public boolean addAll(final Collection<? extends HttpString> c) {
        return addAll(0, c);
    }

    /**
     * Get this value collection as a collection of strings.
     *
     * @return the mapped collection
     */
    public AsStrings asStrings() {
        AsStrings asStrings = this.asStrings;
        if (asStrings == null) {
            asStrings = this.asStrings = new AsStrings();
        }
        return asStrings;
    }

    private static String stringOf(Object obj) {
        return obj == null ? null : obj.toString();
    }

    class StringIterator implements ListIterator<String> {
        private final ListIterator<HttpString> delegate;

        StringIterator(final ListIterator<HttpString> delegate) {
            this.delegate = delegate;
        }

        public boolean hasNext() {
            return delegate.hasNext();
        }

        public String next() {
            return delegate.next().toString();
        }

        public boolean hasPrevious() {
            return delegate.hasPrevious();
        }

        public String previous() {
            return delegate.previous().toString();
        }

        public int nextIndex() {
            return delegate.nextIndex();
        }

        public int previousIndex() {
            return delegate.previousIndex();
        }

        public void remove() {
            delegate.remove();
        }

        public void set(final String s) {
            delegate.set(new HttpString(s));
        }

        public void add(final String s) {
            delegate.add(new HttpString(s));
        }
    }

    public class AsStrings extends AbstractCollection<String> implements Deque<String>, List<String>, RandomAccess {

        public Iterator<String> iterator() {
            return new StringIterator(HeaderValues.this.iterator(0, true));
        }

        public int size() {
            return HeaderValues.this.size();
        }

        public void addFirst(final String s) {
            HeaderValues.this.addFirst(new HttpString(s));
        }

        public void addLast(final String s) {
            HeaderValues.this.addLast(new HttpString(s));
        }

        public boolean offerFirst(final String s) {
            return HeaderValues.this.offerFirst(new HttpString(s));
        }

        public boolean offerLast(final String s) {
            return HeaderValues.this.offerLast(new HttpString(s));
        }

        public String removeFirst() {
            return stringOf(HeaderValues.this.removeFirst());
        }

        public String removeLast() {
            return stringOf(HeaderValues.this.removeLast());
        }

        public String pollFirst() {
            return stringOf(HeaderValues.this.pollFirst());
        }

        public String pollLast() {
            return stringOf(HeaderValues.this.pollLast());
        }

        public String getFirst() {
            return stringOf(HeaderValues.this.getFirst());
        }

        public String getLast() {
            return stringOf(HeaderValues.this.getLast());
        }

        public String peekFirst() {
            return stringOf(HeaderValues.this.peekFirst());
        }

        public String peekLast() {
            return stringOf(HeaderValues.this.peekLast());
        }

        public boolean removeFirstOccurrence(final Object o) {
            return HeaderValues.this.removeFirstOccurrence(o == null ? null : new HttpString(o.toString()));
        }

        public boolean removeLastOccurrence(final Object o) {
            return HeaderValues.this.removeLastOccurrence(o == null ? null : new HttpString(o.toString()));
        }

        public boolean offer(final String s) {
            return HeaderValues.this.offer(new HttpString(s));
        }

        public String remove() {
            return stringOf(HeaderValues.this.remove());
        }

        public String poll() {
            return stringOf(HeaderValues.this.poll());
        }

        public String element() {
            return stringOf(HeaderValues.this.element());
        }

        public String peek() {
            return stringOf(HeaderValues.this.peek());
        }

        public void push(final String s) {
            HeaderValues.this.push(new HttpString(s));
        }

        public String pop() {
            return stringOf(HeaderValues.this.pop());
        }

        public Iterator<String> descendingIterator() {
            return new StringIterator(HeaderValues.this.iterator(0, false));
        }

        public boolean addAll(final int index, final Collection<? extends String> c) {
            return false;
        }

        public String get(final int index) {
            return stringOf(HeaderValues.this.get(index));
        }

        public String set(final int index, final String element) {
            return stringOf(HeaderValues.this.set(index, new HttpString(element)));
        }

        public void add(final int index, final String element) {
            HeaderValues.this.add(index, new HttpString(element));
        }

        public String remove(final int index) {
            return stringOf(HeaderValues.this.remove(index));
        }

        public int indexOf(final Object o) {
            return HeaderValues.this.indexOf(new HttpString(stringOf(o)));
        }

        public int lastIndexOf(final Object o) {
            return HeaderValues.this.lastIndexOf(new HttpString(stringOf(o)));
        }

        public ListIterator<String> listIterator() {
            return new StringIterator(HeaderValues.this.iterator(0, true));
        }

        public ListIterator<String> listIterator(final int index) {
            return new StringIterator(HeaderValues.this.iterator(index, true));
        }

        public List<String> subList(final int fromIndex, final int toIndex) {
            throw new UnsupportedOperationException();
        }
    }
}

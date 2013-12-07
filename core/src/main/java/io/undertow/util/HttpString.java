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

package io.undertow.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;

import static java.lang.Integer.signum;
import static java.lang.Math.abs;
import static java.lang.Math.min;
import static java.lang.System.arraycopy;
import static java.util.Arrays.copyOfRange;

/**
 * An HTTP case-insensitive Latin-1 string.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpString implements Comparable<HttpString>, Serializable {

    /**
     * A case-sensitive comparator for {@code HttpString}s.
     */
    public static final Comparator<HttpString> CASE_SENSITIVE_COMPARATOR = new Comparator<HttpString>() {
        public int compare(final HttpString o1, final HttpString o2) {
            return o1.compareTo(o2);
        }
    };

    /**
     * A case-insensitive comparator for {@code HttpString}s.
     */
    public static final Comparator<HttpString> CASE_INSENSITIVE_COMPARATOR = new Comparator<HttpString>() {
        public int compare(final HttpString o1, final HttpString o2) {
            return o1.compareToIgnoreCase(o2);
        }
    };

    private static final long serialVersionUID = -6359344368200312000L;

    private static final byte[] hi;

    static {
        final byte[] bytes = new byte[128];
        int up;
        for (int i = 128; i < 256; i ++) {
            up = Character.toUpperCase(i);
            bytes[i - 128] = (byte) (up > 256 ? i : up);
        }
        hi = bytes;
    }

    private final byte[] bytes;
    private transient int hashCode;
    private transient int hashCodeIgnoreCase;
    private transient String string;

    /**
     * Empty HttpString instance.
     */
    public static final HttpString EMPTY = new HttpString("");

    /**
     * Construct a new instance.
     *
     * @param bytes the byte array to copy
     */
    public HttpString(final byte[] bytes) {
        this(bytes.clone(), null);
    }

    /**
     * Construct a new instance.
     *
     * @param bytes  the byte array to copy
     * @param offset the offset into the array to start copying
     * @param length the number of bytes to copy
     */
    public HttpString(final byte[] bytes, int offset, int length) {
        this(copyOfRange(bytes, offset, length), null);
    }

    /**
     * Construct a new instance by reading the remaining bytes from a buffer.
     *
     * @param buffer the buffer to read
     */
    public HttpString(final ByteBuffer buffer) {
        this(take(buffer), null);
    }

    /**
     * Construct a new instance from a {@code String}.  The {@code String} will be used
     * as the cached {@code toString()} value for this {@code HttpString}.
     *
     * @param string the source string
     */
    public HttpString(final String string) {
        final int len = string.length();
        final byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            char c = string.charAt(i);
            if (c > 0xff) {
                throw new IllegalArgumentException("Invalid string contents");
            }
            bytes[i] = (byte) c;
        }
        this.string = string;
        this.bytes = bytes;
    }

    private HttpString(final byte[] bytes, final String string) {
        assert bytes != null;
        this.bytes = bytes;
        this.string = string;
    }

    /**
     * Get an {@code HttpString} from a {@code String}.  Common string values may be cached.
     *
     * @param string the source string
     * @return the HTTP string
     */
    public static HttpString fromString(String string) {
        HttpString httpString = Headers.headerFromString(string);
        if (httpString == null) httpString = new HttpString(string);
        return httpString;
    }

    /**
     * Attempt to convert a {@code String} to an {@code HttpString}.  If the string cannot be converted,
     * {@code null} is returned.
     *
     * @param string the string to try
     * @return the HTTP string, or {@code null} if the string is not in a compatible encoding
     */
    public static HttpString tryFromString(String string) {
        HttpString httpString = Headers.headerFromString(string);
        if (httpString != null) {
            return httpString;
        }
        final int len = string.length();
        final byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            char c = string.charAt(i);
            if (c > 0xff) {
                return null;
            }
            bytes[i] = (byte) c;
        }
        return new HttpString(bytes, string);
    }

    /**
     * Get the string length.
     *
     * @return the string length
     */
    public int length() {
        return bytes.length;
    }

    /**
     * Get the byte at an index.
     *
     * @return the byte at an index
     */
    public byte byteAt(int idx) {
        return bytes[idx];
    }

    /**
     * Copy {@code len} bytes from this string at offset {@code srcOffs} to the given array at the given offset.
     *
     * @param srcOffs the source offset
     * @param dst     the destination
     * @param offs    the destination offset
     * @param len     the number of bytes to copy
     */
    public void copyTo(int srcOffs, byte[] dst, int offs, int len) {
        arraycopy(bytes, srcOffs, dst, offs, len);
    }

    /**
     * Copy {@code len} bytes from this string to the given array at the given offset.
     *
     * @param dst  the destination
     * @param offs the destination offset
     * @param len  the number of bytes
     */
    public void copyTo(byte[] dst, int offs, int len) {
        copyTo(0, dst, offs, len);
    }

    /**
     * Copy all the bytes from this string to the given array at the given offset.
     *
     * @param dst  the destination
     * @param offs the destination offset
     */
    public void copyTo(byte[] dst, int offs) {
        copyTo(dst, offs, length());
    }

    /**
     * Append to a byte buffer.
     *
     * @param buffer the buffer to append to
     */
    public void appendTo(ByteBuffer buffer) {
        buffer.put(bytes);
    }

    /**
     * Append as many bytes as possible to a byte buffer.
     *
     * @param offs the start offset
     * @param buffer the buffer to append to
     * @return the number of bytes appended
     */
    public int tryAppendTo(final int offs, final ByteBuffer buffer) {
        final byte[] b = bytes;
        final int len = min(buffer.remaining(), b.length - offs);
        buffer.put(b, offs, len);
        return len;
    }

    /**
     * Append to an output stream.
     *
     * @param output the stream to write to
     * @throws IOException if an error occurs
     */
    public void writeTo(OutputStream output) throws IOException {
        output.write(bytes);
    }

    private static byte[] take(final ByteBuffer buffer) {
        if (buffer.hasArray()) {
            // avoid useless array clearing
            try {
                return copyOfRange(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
            } finally {
                buffer.position(buffer.limit());
            }
        } else {
            final byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
    }

    /**
     * Compare this string to another in a case-sensitive manner.
     *
     * @param other the other string
     * @return -1, 0, or 1
     */
    @Override
    public int compareTo(final HttpString other) {
        if (other == this) return 0;
        return arrayCompareTo(bytes, other.bytes);
    }

    /**
     * Compare this string to another in a case-insensitive manner.
     *
     * @param other the other string
     * @return -1, 0, or 1
     */
    public int compareToIgnoreCase(final HttpString other) {
        if (other == this) return 0;
        return arrayCompareToIgnoreCase(bytes, other.bytes);
    }

    private static int arrayCompareTo(byte[] bytes, byte[] otherBytes) {
        final int length = bytes.length;
        final int otherLength = otherBytes.length;
        // shorter strings sort higher
        if (length != otherLength) return signum(length - otherLength);
        final int len = min(length, otherLength);
        int res;
        for (int i = 0; i < len; i++) {
            res = signum(bytes[i] - otherBytes[i]);
            if (res != 0) return res;
        }
        // it am unpossible
        throw new IllegalStateException();
    }

    private static int arrayCompareToIgnoreCase(byte[] bytes, byte[] otherBytes) {
        final int length = bytes.length;
        final int otherLength = otherBytes.length;
        // shorter strings sort higher
        if (length != otherLength) return signum(length - otherLength);
        final int len = min(length, otherLength);
        int res;
        for (int i = 0; i < len; i++) {
            res = signum(upperCase(bytes[i]) - upperCase(otherBytes[i]));
            if (res != 0) return res;
        }
        // it am unpossible
        throw new IllegalStateException();
    }

    /**
     * Get the hash code.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        int hashCode = this.hashCode;
        if (hashCode == 0) {
            final String string = this.string;
            hashCode = this.hashCode = string != null ? string.hashCode() : calcHashCode(bytes);
        }
        return hashCode;
    }

    /**
     * Get the hash code, ignoring case.
     *
     * @return the hash code
     */
    public int hashCodeIgnoreCase() {
        int hashCode = this.hashCodeIgnoreCase;
        if (hashCode == 0) {
            hashCode = this.hashCodeIgnoreCase = calcHashCodeIgnoreCase(bytes);
        }
        return hashCode;
    }

    /**
     * Determine if this {@code HttpString} is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(final Object other) {
        return other == this || other instanceof HttpString && equals((HttpString) other);
    }

    /**
     * Determine if this {@code HttpString} is equal to another, including case.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final HttpString other) {
        if (other == this) return true;
        if (other == null) return false;
        return Arrays.equals(bytes, other.bytes);
    }

    /**
     * Determine if this {@code HttpString} is equal to another, ignoring case.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equalsIgnoreCase(final HttpString other) {
        if (other == this) return true;
        if (other == null) return false;
        return arrayEqualIgnoreCase(bytes, other.bytes);
    }

    private static int calcHashCode(final byte[] bytes) {
        int hc = 31;
        for (byte b : bytes) {
            hc = (hc << 5) - hc + (b & 0xff);
        }
        return hc;
    }

    private static int calcHashCodeIgnoreCase(final byte[] bytes) {
        int hc = 17;
        for (byte b : bytes) {
            hc = (hc << 4) + hc + upperCase(b);
        }
        return hc;
    }

    private static int upperCase(byte b) {
        return b >= 'a' && b <= 'z' ? b & 0xDF : b < 0 ? hi[b & 0x7f] : b;
    }

    private static boolean arrayEqualIgnoreCase(final byte[] a, final byte[] b) {
        final int len = a.length;
        if (len != b.length) return false;
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i] && upperCase(a[i]) != upperCase(b[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the {@code String} representation of this {@code HttpString}.
     *
     * @return the string
     */
    @Override
    @SuppressWarnings("deprecation")
    public String toString() {
        String string = this.string;
        if (string == null) {
            string = this.string = new String(bytes, 0);
        }
        return string;
    }

    /**
     * Get the {@code String} representation of the given {@code HttpString}.
     *
     * @param httpString the HTTP string
     * @return the string or {@code null} if {@code httpString} was {@code null}
     */
    public static String toString(HttpString httpString) {
        return httpString == null ? null : httpString.toString();
    }

    static int hashCodeIgnoreCaseOf(String headerName) {
        int hc = 17;

        for (int i = 0; i < headerName.length(); ++i) {
            hc = (hc << 4) + hc + upperCase((byte) headerName.charAt(i));
        }
        return hc;
    }

    /**
     * Get a substring of this {@code HttpString} starting at {@code offs}.
     *
     * @param begin the starting offset
     * @return the substring
     */
    public HttpString substring(int begin) {
        byte[] bytes = this.bytes;
        String string = this.string;
        if (string != null) {
            return new HttpString(Arrays.copyOfRange(bytes, begin, bytes.length), string.substring(begin));
        } else {
            return new HttpString(Arrays.copyOfRange(bytes, begin, bytes.length), null);
        }
    }

    /**
     * Get a substring of this {@code HttpString} starting at {@code offs} going to {@code end} (exclusive).
     *
     * @param begin the starting offset
     * @param end the end position (exclusive)
     * @return the substring
     */
    public HttpString substring(int begin, int end) {
        byte[] bytes = this.bytes;
        String string = this.string;
        if (string != null) {
            return new HttpString(Arrays.copyOfRange(bytes, begin, end), string.substring(begin, end));
        } else {
            if (begin < 0 || end > bytes.length) throw new IndexOutOfBoundsException();
            return new HttpString(Arrays.copyOfRange(bytes, begin, end), null);
        }
    }

    /**
     * Get the unsigned {@code int} value of this string.  If the value is greater than would fit in 32 bits, only
     * the low 32 bits are returned.  Parsing stops on the first non-digit character.
     *
     * @param start the index to start at (must be less than or equal to length)
     * @return the value
     */
    public int toInt(final int start) {
        final byte[] bytes = this.bytes;
        int v = 0;
        final int len = bytes.length;
        byte b;
        for (int i = start; i < len; i ++) {
            b = bytes[i];
            if (b < '0' || b > '9') {
                return v;
            }
            v = (v << 3) + (v << 1) + (b - '0');
        }
        return v;
    }

    /**
     * Get the unsigned {@code int} value of this string.  If the value is greater than would fit in 32 bits, only
     * the low 32 bits are returned.  Parsing stops on the first non-digit character.
     *
     * @return the value
     */
    public int toInt() {
        return toInt(0);
    }

    /**
     * Get the unsigned {@code long} value of this string.  If the value is greater than would fit in 64 bits, only
     * the low 64 bits are returned.  Parsing stops on the first non-digit character.
     *
     * @param start the index to start at (must be less than or equal to length)
     * @return the value
     */
    public long toLong(final int start) {
        final byte[] bytes = this.bytes;
        long v = 0;
        final int len = bytes.length;
        byte b;
        for (int i = start; i < len; i ++) {
            b = bytes[i];
            if (b < '0' || b > '9') {
                return v;
            }
            v = (v << 3) + (v << 1) + (b - '0');
        }
        return v;
    }

    /**
     * Get the unsigned {@code long} value of this string.  If the value is greater than would fit in 64 bits, only
     * the low 64 bits are returned.  Parsing stops on the first non-digit character.
     *
     * @return the value
     */
    public long toLong() {
        return toLong(0);
    }

    private static int decimalCount(int val) {
        assert val >= 0;
        // afaik no faster way exists to do this
        if (val < 10) return 1;
        if (val < 100) return 2;
        if (val < 1000) return 3;
        if (val < 10000) return 4;
        if (val < 100000) return 5;
        if (val < 1000000) return 6;
        if (val < 10000000) return 7;
        if (val < 100000000) return 8;
        if (val < 1000000000) return 9;
        return 10;
    }

    private static int decimalCount(long val) {
        assert val >= 0;
        // afaik no faster way exists to do this
        if (val < 10L) return 1;
        if (val < 100L) return 2;
        if (val < 1000L) return 3;
        if (val < 10000L) return 4;
        if (val < 100000L) return 5;
        if (val < 1000000L) return 6;
        if (val < 10000000L) return 7;
        if (val < 100000000L) return 8;
        if (val < 1000000000L) return 9;
        if (val < 10000000000L) return 10;
        if (val < 100000000000L) return 11;
        if (val < 1000000000000L) return 12;
        if (val < 10000000000000L) return 13;
        if (val < 100000000000000L) return 14;
        if (val < 1000000000000000L) return 15;
        if (val < 10000000000000000L) return 16;
        if (val < 100000000000000000L) return 17;
        if (val < 1000000000000000000L) return 18;
        return 19;
    }

    private static final HttpString ZERO = new HttpString(new byte[] { '0' }, "0");

    /**
     * Get a string version of the given value.
     *
     * @param val the value
     * @return the string
     */
    public static HttpString fromLong(long val) {
        if (val == 0) return ZERO;
        // afaik no faster way exists to do this
        int i = decimalCount(abs(val));
        final byte[] b;
        if (val < 0) {
            b = new byte[++i];
            b[0] = '-';
        } else {
            b = new byte[i];
        }
        long quo;
        // modulus
        int mod;
        do {
            quo = val / 10;
            mod = (int) (val - ((quo << 3) + (quo << 1)));
            b[--i] = (byte) (mod + '0');
            val = quo;
        } while (i > 0);
        return new HttpString(b, null);
    }

    /**
     * Get a string version of the given value.
     *
     * @param val the value
     * @return the string
     */
    public static HttpString fromInt(int val) {
        if (val == 0) return ZERO;
        // afaik no faster way exists to do this
        int i = decimalCount(abs(val));
        final byte[] b;
        if (val < 0) {
            b = new byte[++i];
            b[0] = '-';
        } else {
            b = new byte[i];
        }
        int quo;
        // modulus
        int mod;
        do {
            quo = val / 10;
            mod = (int) (val - ((quo << 3) + (quo << 1)));
            b[--i] = (byte) (mod + '0');
            val = quo;
        } while (i > 0);
        return new HttpString(b, null);
    }

    /**
     * Determine whether this {@code HttpString} is equal (case-sensitively) to the given {@code String}.
     *
     * @param str the string to check
     * @return {@code true} if the given string is equal (case-sensitively) to this instance, {@code false} otherwise
     */
    public boolean equalToString(String str) {
        if (str == null) return false;
        final String string = this.string;
        if (string != null) {
            return str.equals(string);
        }
        final byte[] bytes = this.bytes;
        final int length = bytes.length;
        if (str.length() != length) {
            return false;
        }
        final int len = length;
        char ch;
        for (int i = 0; i < len; i++) {
            ch = str.charAt(i);
            if (ch > 0xff || bytes[i] != (byte) str.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determine whether this {@code HttpString} is equal (case-insensitively) to the given {@code String}.
     *
     * @param str the string to check
     * @return {@code true} if the given string is equal (case-insensitively) to this instance, {@code false} otherwise
     */
    public boolean equalToStringIgnoreCase(String str) {
        if (str == null) return false;
        final String string = this.string;
        if (string != null) {
            return str.equalsIgnoreCase(string);
        }
        final byte[] bytes = this.bytes;
        final int length = bytes.length;
        if (str.length() != length) {
            return false;
        }
        final int len = length;
        char ch;
        for (int i = 0; i < len; i++) {
            ch = str.charAt(i);
            if (ch > 0xff || upperCase(bytes[i]) != upperCase((byte) ch)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the index of the given character in this string.
     *
     * @param c the character
     * @return the index, or -1 if it was not found
     */
    public int indexOf(final char c) {
        if (c > 255) {
            return -1;
        }
        final byte[] bytes = this.bytes;
        final byte bc = (byte) c;
        for (int i = 0, bytesLength = bytes.length; i < bytesLength; i++) {
            if (bytes[i] == bc) {
                return i;
            }
        }
        return -1;
    }

    private static boolean arrayContains(byte[] a, byte[] b) {
        final int aLen = a.length;
        final int bLen = b.length;
        if (bLen > aLen) {
            return false;
        }
        OUTER: for (int i = 0; i < aLen - bLen; i ++) {
            if (a[i] == b[0]) {
                for (int j = 0; j < bLen; j ++) {
                    if (a[i + j] != b[j]) {
                        continue OUTER;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Determine whether this string contains another string (case-sensitive).
     *
     * @param other the string to test
     * @return {@code true} if this string contains {@code other}, {@code false} otherwise
     */
    public boolean contains(final HttpString other) {
        if (other == this) return true;
        if (other == null) return false;
        return arrayContains(bytes, other.bytes);
    }
}

/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.svcs.errorhandling.utils;

import static com.emc.storageos.svcs.errorhandling.utils.Messages.localize;
import static junit.framework.Assert.assertEquals;

import java.util.Calendar;
import java.util.Locale;

import org.junit.Test;

public class MessagesTest {

    private static final Locale LOCALE = Locale.US;
    private final String bundleName = getClass().getName();

    @Test
    public void invalidPattern() {
        // This should return the pattern+parameters if the pattern is invalid
        final String string = localize(bundleName, LOCALE, "invalidPattern",
                (Object[]) null);
        assertEquals(
                "This is an invalid pattern {} (locale=en_US,bundle=com.emc.storageos.svcs.errorhandling.utils.MessagesTest,key=invalidPattern,arguments=null)",
                string);
    }

    @Test
    public void nullArgs() {
        final String string = localize(bundleName, LOCALE, "nullArgs", (Object[]) null);
        assertEquals("test {0},{1}", string);
    }

    @Test
    public void moreArgsThanPattern() {
        final String string = localize(bundleName, LOCALE, "moreArgsThanPattern",
                new Object[] { "bob", "fred" });
        assertEquals("test bob", string);
    }

    @Test
    public void moreArgsInPattern() {
        final String string = localize(bundleName, LOCALE, "moreArgsInPattern",
                new Object[] { "1", "2" });
        assertEquals("test 1,2,{3}", string);
    }

    @Test
    public void nullInArgs() {
        final String string = localize(bundleName, LOCALE, "nullInArgs",
                new Object[] { null });
        assertEquals("test null", string);
    }

    @Test
    public void wrongArgType() {
        // This should return the pattern+parameters if the type is invalid
        final String string = localize(bundleName, LOCALE, "wrongArgType",
                new Object[] { "bob" });
        assertEquals(
                "test {0,integer} (locale=en_US,bundle=com.emc.storageos.svcs.errorhandling.utils.MessagesTest,key=wrongArgType,arguments=[bob])",
                string);
    }

    // Tests for converting arrays

    @Test
    public void notConvertDate() {
        final Calendar calendar = Calendar.getInstance();
        calendar.set(2000, 1, 1, 0, 0, 0);
        assertEquals("2/1/00 12:00 AM",
                localize(null, LOCALE, "{0}", new Object[] { calendar.getTime() }));
    }

    @Test
    public void notConvertNumber() {
        assertEquals("1", localize(null, LOCALE, "{0}", 1));
    }

    @Test
    public void onlyConvertArray() {
        final Calendar calendar = Calendar.getInstance();
        calendar.set(2000, 1, 1, 0, 0, 0);
        assertEquals(
                "1, [test, 1], Feb 1, 2000",
                localize(null, LOCALE, "{0,number}, {1}, {2,date}",
                        new Object[] { 1, new Object[] { "test", 1 },
                                calendar.getTime() }));
    }

    @Test
    public void convertPrimitiveArray() {
        final boolean[] booleans = { true, false, false };
        final char[] chars = { 'B', 'P', 'H' };
        final byte[] bytes = { 3 };
        final short[] shorts = { 5, 6 };
        final int[] ints = { 7, 8, 9, 10 };
        final long[] longs = { 100, 101, 102 };
        final float[] floats = { 99.9f, 63.2f };
        final double[] doubles = { 212.2, 16.236, 42.2 };
        final int[] nullInts = null;
        final int[] emptyInts = {};

        assertEquals("[true, false, false]",
                localize(null, LOCALE, "{0}", new Object[] { booleans }));
        assertEquals("[B, P, H]", localize(null, LOCALE, "{0}", new Object[] { chars }));
        assertEquals("[3]", localize(null, LOCALE, "{0}", new Object[] { bytes }));
        assertEquals("[5, 6]", localize(null, LOCALE, "{0}", new Object[] { shorts }));
        assertEquals("[7, 8, 9, 10]",
                localize(null, LOCALE, "{0}", new Object[] { ints }));
        assertEquals("[100, 101, 102]",
                localize(null, LOCALE, "{0}", new Object[] { longs }));
        assertEquals("[99.9, 63.2]",
                localize(null, LOCALE, "{0}", new Object[] { floats }));
        assertEquals("[212.2, 16.236, 42.2]",
                localize(null, LOCALE, "{0}", new Object[] { doubles }));
        assertEquals("null", localize(null, LOCALE, "{0}", new Object[] { nullInts }));
        assertEquals("[]", localize(null, LOCALE, "{0}", new Object[] { emptyInts }));
    }

    @Test
    public void convertStringArray() {
        final String[] strings = { "blah", "blah", "blah" };
        final String[] emptyStrings = { "", "" };
        final String[] nullStrings = { null, null };

        assertEquals("[blah, blah, blah]",
                localize(null, LOCALE, "{0}", new Object[] { strings }));
        assertEquals("[, ]", localize(null, LOCALE, "{0}", new Object[] { emptyStrings }));
        assertEquals("[null, null]",
                localize(null, LOCALE, "{0}", new Object[] { nullStrings }));
    }

    @Test
    public void convertNestedArray() {
        final String[] arrayA = { "A", "a" };
        final String[] arrayB = { "B", "b" };
        final String[][] arrayOfArrays = { arrayA, arrayB };
        assertEquals("[[A, a], [B, b]]",
                localize(null, LOCALE, "{0}", new Object[] { arrayOfArrays }));
    }
}

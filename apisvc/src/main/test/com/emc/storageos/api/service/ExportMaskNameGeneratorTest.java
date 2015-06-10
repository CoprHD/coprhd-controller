/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.api.service;

import com.emc.storageos.db.client.util.ExportMaskNameGenerator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExportMaskNameGeneratorTest {

    private final static ExportMaskNameGenerator emg = new ExportMaskNameGenerator();
    public static final int MAXLENGTH = 64;

    @Test
    public void testAllNamesProvidedShort() {
        String cluster = "abc";
        String alternate = "zyx";
        String hostname = "host123-emc-com";

        String generatedName = emg.generate(cluster, hostname, alternate);
        assertTrue(String.format("Generated name '%s' is longer than max characters. Length is %d",
                generatedName, generatedName.length()), generatedName.length() <= MAXLENGTH);

        String expected = alternate + "_" + cluster + "_" + hostname;
        assertEquals(expected, generatedName);

        System.out.println(String.format("testAllNamesProvidedShort - Generated name '%s', length is %d", generatedName, generatedName.length()));
    }

    @Test
    public void testAllNamesProvidedLongClusterName() {
        String cluster = "abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz";
        String alternate = "xyz";
        String hostname = "host123";

        String generatedName = emg.generate(cluster, hostname, alternate);
        assertTrue(String.format("Generated name '%s' is longer than max characters. Length is %d",
                generatedName, generatedName.length()), generatedName.length() <= MAXLENGTH);

        int truncate = MAXLENGTH - (hostname.length() + alternate.length() + 2);
        String expected = alternate + "_" + cluster.substring(0, truncate) + "_" + hostname;
        assertEquals(expected, generatedName);

        System.out.println(String.format("testAllNamesProvidedLongClusterName - Generated name '%s', length is %d", generatedName, generatedName.length()));
    }

    @Test
    public void testAllNamesProvidedLongAlternateName() {
        String cluster = "abc";
        String alternate = "zyxwvutsrqponmlkjihgfedcba-zyxwvutsrqponmlkjihgfedcba-zyxwvutsrqponmlkjihgfedcba";
        String hostname = "host123";

        String generatedName = emg.generate(cluster, hostname, alternate);
        assertTrue(String.format("Generated name '%s' is longer than max characters. Length is %d",
                generatedName, generatedName.length()), generatedName.length() <= MAXLENGTH);

        int truncate = MAXLENGTH - (hostname.length() + cluster.length() + 2);
        String expected = alternate.substring(0, truncate) + "_" + cluster + "_" + hostname;
        assertEquals(expected, generatedName);

        System.out.println(String.format("testAllNamesProvidedLongAlternateName - Generated name '%s', length is %d", generatedName, generatedName.length()));
    }

    @Test
    public void testAllNamesProvidedLongHostName() {
        String cluster = "abc";
        String alternate = "zyx";
        String hostname = "host123456789012345678901234567890123456789012345678901234567890-emc-com";

        String generatedName = emg.generate(cluster, hostname, alternate);
        assertTrue(String.format("Generated name '%s' is longer than max characters. Length is %d",
                generatedName, generatedName.length()), generatedName.length() <= MAXLENGTH);

        String expected = hostname.substring(0, MAXLENGTH);
        assertEquals(expected, generatedName);

        System.out.println(String.format("testAllNamesProvidedLongHostName - Generated name '%s', length is %d", generatedName, generatedName.length()));
    }

    @Test
    public void testAllNamesProvidedAllLong() {
        String cluster = "abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz";
        String alternate = "zyxwvutsrqponmlkjihgfedcba-zyxwvutsrqponmlkjihgfedcba-zyxwvutsrqponmlkjihgfedcba";
        String hostname = "host123456789012345678901234567890123456789012345678901234567890-emc-com";

        String generatedName = emg.generate(cluster, hostname, alternate);
        assertTrue(String.format("Generated name '%s' is longer than max characters. Length is %d",
                generatedName, generatedName.length()), generatedName.length() <= MAXLENGTH);

        String expected = hostname.substring(0, MAXLENGTH);
        assertEquals(expected, generatedName);

        System.out.println(String.format("testAllNamesProvidedAllLong - Generated name '%s', length is %d", generatedName, generatedName.length()));
    }

    @Test
    public void testNoAlternateName() {
        String cluster = "abc";
        String alternate = null;
        String hostname = "host123-emc-com";

        String generatedName = emg.generate(cluster, hostname, alternate);
        assertTrue(String.format("testNoAlternateName - Generated name '%s' is longer than max characters. Length is %d",
                generatedName, generatedName.length()), generatedName.length() <= MAXLENGTH);

        String expected = cluster + "_" + hostname;
        assertEquals(expected, generatedName);

        System.out.println(String.format("testNoAlternateName - Generated name '%s', length is %d", generatedName, generatedName.length()));
    }

    @Test
    public void testNoClusterName() {
        String cluster = null;
        String alternate = "xyz";
        String hostname = "host123-emc-com";

        String generatedName = emg.generate(cluster, hostname, alternate);
        assertTrue(String.format("Generated name '%s' is longer than max characters. Length is %d",
                generatedName, generatedName.length()), generatedName.length() <= MAXLENGTH);

        String expected = alternate + "_" + hostname;
        assertEquals(expected, generatedName);

        System.out.println(String.format("testNoClusterName - Generated name '%s', length is %d", generatedName, generatedName.length()));
    }

    @Test
    public void testNoAlternateNameLongClusterName() {
        String cluster = "abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz";
        String alternate = null;
        String hostname = "host123-emc-com";

        String generatedName = emg.generate(cluster, hostname, alternate);
        assertTrue(String.format("Generated name '%s' is longer than max characters. Length is %d",
                generatedName, generatedName.length()), generatedName.length() <= MAXLENGTH);

        int truncate = MAXLENGTH - (hostname.length() +  1);
        String expected = cluster.substring(0, truncate) + "_" + hostname;
        assertEquals(expected, generatedName);

        System.out.println(String.format("testNoAlternateNameLongClusterName - Generated name '%s', length is %d", generatedName, generatedName.length()));
    }

    @Test
    public void testNoClusterNameLongAlternateName() {
        String cluster = null;
        String alternate = "zyxwvutsrqponmlkjihgfedcba-zyxwvutsrqponmlkjihgfedcba-zyxwvutsrqponmlkjihgfedcba";
        String hostname = "host123-emc-com";

        String generatedName = emg.generate(cluster, hostname, alternate);
        assertTrue(String.format("Generated name '%s' is longer than max characters. Length is %d",
                generatedName, generatedName.length()), generatedName.length() <= MAXLENGTH);

        int truncate = MAXLENGTH - (hostname.length() + 1);
        String expected = alternate.substring(0, truncate) + "_" + hostname;
        assertEquals(expected, generatedName);

        System.out.println(String.format("testNoClusterNameLongAlternateName - Generated name '%s', length is %d", generatedName, generatedName.length()));
    }
}

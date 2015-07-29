/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.scsi;

import com.google.common.collect.Maps;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class DeviceIdentificationTest {

    @Test
    public void testGetWwid() throws Exception {
        Map<String, String> wwidTests = Maps.newLinkedHashMap();
        // VMAX
        wwidTests.put(
                "0083001C01030010" +
                        "6000000000000001" +
                        "1234567890123456" +
                        "1000000000000000",
                "60000000000000011234567890123456"
                );

        // VNX
        wwidTests.put(
                "0083003801030010" +
                        "6000000000000005" +
                        "ABC123ABC123ABC1" +
                        "0100001000000000" +
                        "0000000000B50000" +
                        "0000000001140004" +
                        "0000000D01150004" +
                        "00000002",
                "6000000000000005ABC123ABC123ABC1"
                );

        // Hitachi HDS
        wwidTests.put(
                "0083003202010014" +
                        "1000000000000000" +
                        "1000000000000000" +
                        "3030373001100002" +
                        "0400010300106006" +
                        "123456D789012345" +
                        "2D00BB00BB00",
                "6006123456D7890123452D00BB00BB00"
                );

        // LSI
        wwidTests.put(
                "0083001401030010" +
                        "6000000000000009" +
                        "19AB123CD321EFAB",
                "600000000000000919AB123CD321EFAB"
                );

        for (Map.Entry<String, String> entry : wwidTests.entrySet()) {
            byte[] bytes = Hex.decodeHex(entry.getKey().toCharArray());

            // Print each found identifier for information
            System.out.println("Identifiers found for WWID: " + entry.getValue());
            for (Map.Entry<Integer, String> identifier : DeviceIdentification.parseIdentifiers(bytes).entrySet()) {
                System.out.println(String.format(" %s - %s", identifier.getKey(), identifier.getValue()));
            }

            String wwid = DeviceIdentification.getWwid(bytes);
            assertEquals(entry.getValue(), wwid.toUpperCase());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetWwidTooShort() throws Exception {
        byte[] bytes = Hex.decodeHex("BADDBEEF".toCharArray());
        DeviceIdentification.getWwid(bytes);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetWwidGarbage() throws Exception {
        byte[] bytes = Hex.decodeHex("BADDBEEFBADDBEEFBADDBEEF".toCharArray());
        DeviceIdentification.getWwid(bytes);
    }
}

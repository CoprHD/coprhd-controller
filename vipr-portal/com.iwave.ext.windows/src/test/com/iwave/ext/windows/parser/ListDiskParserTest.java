/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.parser;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.junit.Test;

import com.iwave.ext.windows.model.DiskSummary;

;

public class ListDiskParserTest {

    private static final String LIST_DISK_FILENAME = "ListDisk";

    private static final String ONLINE = "Online";
    private static final String OFFLINE = "Offline";
    private static final String RESERVED = "Reserved";
    private static Logger log = Logger.getLogger(ListDiskParserTest.class);

    private static final String FLAG = "*";

    @Test
    public void test() {
        String listDiskOutput = readFile(LIST_DISK_FILENAME);
        List<DiskSummary> devices = new ListDiskParser().parseDevices(listDiskOutput);
        assertEquals(8, devices.size());
        assertDevice1(devices.get(0));
        assertDevice2(devices.get(1));
        assertDevice4(devices.get(2));
    }

    private void assertDevice1(DiskSummary device) {
        assertEquals("Disk 0", device.getDiskNumber());
        assertEquals(ONLINE, device.getStatus());
        assertEquals(FLAG, device.getDyn());
        assertEquals(" ", device.getGpt());
    }

    private void assertDevice2(DiskSummary device) {
        assertEquals("Disk 5", device.getDiskNumber());
        assertEquals(ONLINE, device.getStatus());
        assertEquals(" ", device.getDyn());
        assertEquals(FLAG, device.getGpt());
    }

    private void assertDevice4(DiskSummary device) {
        assertEquals("Disk 9", device.getDiskNumber());
        assertEquals(OFFLINE, device.getStatus());
        assertEquals(FLAG, device.getDyn());
        assertEquals(FLAG, device.getGpt());
    }

    protected String readFile(String filename) {
        InputStream diskUtilFile = getClass().getResourceAsStream(filename);

        StringWriter writer = new StringWriter();
        try {
            IOUtils.copy(diskUtilFile, writer);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        String text = writer.toString();
        return text;
    }

}

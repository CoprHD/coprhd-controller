/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.parser;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.iwave.ext.linux.model.PowerPathDevice;

public class PowerPathHDSInquiryParserTest {

    private static final String POWER_PATH_INQUIRY_FILENAME = "pp_inqHDS";

    private static final String HITACHI = "HITACHI";
    private static final String HDS = "HDS";

    @Test
    public void test() {
        String ppinqOutput = readFile(POWER_PATH_INQUIRY_FILENAME);
        List<PowerPathDevice> devices = new PowerPathHDSInquiryParser()
                .parseDevices(ppinqOutput);
        assertEquals(18, devices.size());
        assertDevice1(devices.get(0));
        assertDevice2(devices.get(1));
        assertDevice4(devices.get(3));
    }

    private void assertDevice1(PowerPathDevice device) {
        assertEquals("/dev/emcpowerbe", device.getDevice());
        assertEquals(HITACHI, device.getVendor());
        assertEquals(HDS, device.getProduct());
        assertEquals("4000000000000000000000000000000000000001", device.getWwn());
    }

    private void assertDevice2(PowerPathDevice device) {
        assertEquals("/dev/emcpowerbg", device.getDevice());
        assertEquals(HITACHI, device.getVendor());
        assertEquals(HDS, device.getProduct());
        assertEquals("4000000000000000000000000000000000000002", device.getWwn());
    }

    private void assertDevice4(PowerPathDevice device) {
        assertEquals("/dev/emcpowerbk", device.getDevice());
        assertEquals(HITACHI, device.getVendor());
        assertEquals(HDS, device.getProduct());
        assertEquals("4000000000000000000000000000000000000004", device.getWwn());
    }

    protected String readFile(String filename) {
        InputStream diskUtilFile = getClass().getResourceAsStream(filename);

        StringWriter writer = new StringWriter();
        try {
            IOUtils.copy(diskUtilFile, writer);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        String text = writer.toString();
        return text;
    }

}

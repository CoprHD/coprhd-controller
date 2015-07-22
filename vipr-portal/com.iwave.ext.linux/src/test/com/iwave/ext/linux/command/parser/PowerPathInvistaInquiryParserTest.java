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
import org.apache.log4j.Logger;
import org.junit.Test;

import com.iwave.ext.linux.model.PowerPathDevice;

public class PowerPathInvistaInquiryParserTest {

    private static final String POWER_PATH_INQUIRY_FILENAME = "pp_inqInvista";

    private static final String EMC = "EMC";
    private static final String INVISTA = "Invista";
    private static Logger log = Logger.getLogger(PowerPathInvistaInquiryParserTest.class);

    @Test
    public void test() {
        String ppinqOutput = readFile(POWER_PATH_INQUIRY_FILENAME);
        List<PowerPathDevice> devices = new PowerPathInvistaInquiryParser().parseDevices(ppinqOutput);
        assertEquals(4, devices.size());
        assertDevice1(devices.get(0));
        assertDevice2(devices.get(1));
        assertDevice4(devices.get(3));
    }

    private void assertDevice1(PowerPathDevice device) {
        assertEquals("/dev/emcpowerff", device.getDevice());
        assertEquals(EMC, device.getVendor());
        assertEquals(INVISTA, device.getProduct());
        assertEquals("600000000000000000000000000000a1", device.getWwn());
    }
    
    private void assertDevice2(PowerPathDevice device) {
        assertEquals("/dev/emcpowerl", device.getDevice());
        assertEquals(EMC, device.getVendor());
        assertEquals(INVISTA, device.getProduct());
        assertEquals("600000000000000000000000000000b2", device.getWwn());
    }

    private void assertDevice4(PowerPathDevice device) {
        assertEquals("/dev/emcpowern", device.getDevice());
        assertEquals(EMC, device.getVendor());
        assertEquals(INVISTA, device.getProduct());
        assertEquals("600000000000000000000000000000d4", device.getWwn());
    }
    
    protected String readFile(String filename) {
        InputStream diskUtilFile = getClass().getResourceAsStream(filename);
        
        StringWriter writer = new StringWriter();
        try {
            IOUtils.copy(diskUtilFile, writer);
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        String text = writer.toString();
        return text;
    }

}

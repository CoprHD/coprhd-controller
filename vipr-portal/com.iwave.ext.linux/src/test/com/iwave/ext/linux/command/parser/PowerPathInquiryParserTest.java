/*
 * Copyright (c) 2012-2015 iWave Software LLC
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

public class PowerPathInquiryParserTest {

	private static final String POWER_PATH_INQUIRY_FILENAME = "pp_inq";

	private static final String VRAID = "VRAID";
	private static final String DGC = "DGC";
	private static final String EMC = "EMC";
	private static final String SYMMETRIX = "SYMMETRIX";
    private static Logger log = Logger.getLogger(PowerPathInquiryParserTest.class);

	@Test
	public void test() {
		String ppinqOutput = readFile(POWER_PATH_INQUIRY_FILENAME);
		List<PowerPathDevice> devices = new PowerPathInquiryParser().parseDevices(ppinqOutput);
		assertEquals(14, devices.size());
		assertDevice1(devices.get(0));
		assertDevice2(devices.get(1));
		assertDevice3(devices.get(2));
		assertDevice14(devices.get(13));
	}

	private void assertDevice1(PowerPathDevice device) {
		assertEquals("/dev/emcpowera", device.getDevice());
		assertEquals(EMC, device.getVendor());
		assertEquals(SYMMETRIX, device.getProduct());
		assertEquals("60000000000000000000000000000001", device.getWwn());
	}

	private void assertDevice2(PowerPathDevice device) {
		assertEquals("/dev/emcpowerb", device.getDevice());
		assertEquals(DGC, device.getVendor());
		assertEquals(VRAID, device.getProduct());
		assertEquals("60000000000000000000000000000002", device.getWwn());
	}

	private void assertDevice3(PowerPathDevice device) {
		assertEquals("/dev/emcpowerc", device.getDevice());
		assertEquals(DGC, device.getVendor());
		assertEquals(VRAID, device.getProduct());
		assertEquals("60000000000000000000000000000003", device.getWwn());
	}

	private void assertDevice14(PowerPathDevice device) {
		assertEquals("/dev/emcpowero", device.getDevice());
		assertEquals(EMC, device.getVendor());
		assertEquals(SYMMETRIX, device.getProduct());
		assertEquals("60000000000000000000000000000014", device.getWwn());
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

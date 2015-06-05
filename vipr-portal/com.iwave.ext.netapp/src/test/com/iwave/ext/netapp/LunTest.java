/**
 * 
 */
package com.iwave.ext.netapp;

import static org.junit.Assert.*;

import netapp.manage.NaServer;

import org.junit.BeforeClass;
import org.junit.Test;


/**
 * @author sdorcas
 *
 */
public class LunTest {

	static private NaServer server = null;
	static private final String VOL_NAME = "test1";
	static private final String LUN_PATH = "/vol/"+VOL_NAME+"/lun1";
	static private final long INIT_LUN_SIZE = (100*1024*1024); // 100Mb in bytes
	static private final long NEW_LUN_SIZE = (120*1024*1024); // 120Mb in bytes
	static private final String INIT_GROUP = "testgroup";
	static private final int LUN_ID = 99;
	
	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Server s = new Server("10.2.1.25", 80, "root", "1Wave$oftware", false);
		server = s.getNaServer();
	}
	
	/**
	 * Test method for {@link com.iwave.ext.netapp.Lun#createLunBySize(com.iwave.ext.netapp.LunOSType, int, boolean)}.
	 */
	@Test
	public void testCreateLunBySize() {
		Lun lun = new Lun(server, LUN_PATH);
		long actualSize = lun.createLunBySize(LunOSType.windows, INIT_LUN_SIZE, true);
		assertEquals(INIT_LUN_SIZE, actualSize);
	}

	/**
	 * Test method for {@link com.iwave.ext.netapp.Lun#mapLun(boolean, java.lang.String, int)}.
	 */
	@Test
	public void testMapLun() {
		Lun lun = new Lun(server, LUN_PATH);
		int result = lun.mapLun(false, INIT_GROUP, LUN_ID);
		assertEquals(LUN_ID, result);
	}

	/**
	 * Test method for {@link com.iwave.ext.netapp.Lun#setLunDescription(java.lang.String)}.
	 */
	@Test
	public void testSetLunDescription() {
		Lun lun = new Lun(server, LUN_PATH);
		lun.setLunDescription("testing");
		assertTrue(true);
	}
	@Test
	public void testSetLunOffline() {
		Lun lun = new Lun(server, LUN_PATH);
		boolean result = lun.setLunOnline(false, false);
		assertTrue(result);
	}
	
	/**
	 * Test method for {@link com.iwave.ext.netapp.Lun#resizeLun(int, boolean)}.
	 */
	@Test
	public void testResizeLun() {
		Lun lun = new Lun(server, LUN_PATH);
		long newSize = lun.resizeLun(NEW_LUN_SIZE, false);
		assertEquals(NEW_LUN_SIZE, newSize);
	}

	/**
	 * Test method for {@link com.iwave.ext.netapp.Lun#setLunOnline(boolean, boolean)}.
	 */
	@Test
	public void testSetLunOnline() {
		Lun lun = new Lun(server, LUN_PATH);
		boolean result = lun.setLunOnline(true, false);
		assertTrue(result);		
	}
	/**
	 * Test method for {@link com.iwave.ext.netapp.Lun#getLunOccupiedSize()}.
	 */
	@Test
	public void testGetLunOccupiedSize() {
		Lun lun = new Lun(server, LUN_PATH);
		long size = lun.getLunOccupiedSize();
		assertEquals(size, 0);
	}
	
	@Test
	public void testSetLunOffline2() {
		Lun lun = new Lun(server, LUN_PATH);
		boolean result = lun.setLunOnline(false, false);
		assertTrue(result);
	}
	
	/**
	 * Test method for {@link com.iwave.ext.netapp.Lun#unmapLun(String)}.
	 */
	@Test
	public void testUnmapLun() {
		Lun lun = new Lun(server, LUN_PATH);
		boolean result = lun.unmapAll();
		assertTrue(result);
	}
	/**
	 * Test method for {@link com.iwave.ext.netapp.Lun#destroyLun(boolean)}.
	 */
	@Test
	public void testDestroyLun() {
		Lun lun = new Lun(server, LUN_PATH);
		boolean result = lun.destroyLun(false);
		assertTrue(result);
	}
}

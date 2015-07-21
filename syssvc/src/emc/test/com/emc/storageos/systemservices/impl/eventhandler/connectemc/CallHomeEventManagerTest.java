/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.eventhandler.connectemc;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.junit.Assert;

import org.junit.Test;

import com.emc.storageos.systemservices.impl.licensing.LicenseInfoExt;


public class CallHomeEventManagerTest {
	
	private class MockCallHomeEventManager extends CallHomeEventManager {
		
		private String lastExpirationEventDate;
		private String lastHeartbeatEventDate;
		private String registrationEventDate;
		private String capacityExceededEventDate;
		
		/**
		 * override due to  call to coordinator service..
		 */
		
		public LicenseInfoExt getLicenseInfo() {
			LicenseInfoExt licenseInfo = new LicenseInfoExt();
			licenseInfo.setLastLicenseExpirationDateEventDate(lastExpirationEventDate);
			licenseInfo.setLastRegistrationEventDate(registrationEventDate);
			licenseInfo.setLastHeartbeatEventDate(lastHeartbeatEventDate);
			licenseInfo.setLastCapacityExceededEventDate(capacityExceededEventDate);			
			return licenseInfo;
		}
		
		/**
		 * override due to call to coordinator service.
		 */
		public void setLicenseInfo(LicenseInfoExt licenseInfo) {
			
			lastExpirationEventDate = licenseInfo.getLastLicenseExpirationDateEventDate();
			lastHeartbeatEventDate = licenseInfo.getLastHeartbeatEventDate();
			registrationEventDate = licenseInfo.getLastRegistrationEventDate();
			capacityExceededEventDate = licenseInfo.getLastCapacityExceededEventDate();
		}
	}
	
	/**
	 * Test case which recognizes that a heartbeat event should be sent.
	 */
	@Test
	public void testHeartbeatEventTrue() throws Exception {
		
		MockCallHomeEventManager manager = new MockCallHomeEventManager();
		LicenseInfoExt licenseInfo = new LicenseInfoExt();
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, CallHomeConstants.HEARTBEART_EVENT_THRESHOLD * -1 );
		SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
		String stringDate = sdf.format(cal.getTime());
		
		
		licenseInfo.setLastHeartbeatEventDate(stringDate);
		manager.setLicenseInfo(licenseInfo);

		Assert.assertTrue(manager.doSendHeartBeat(licenseInfo));
	}
	
	
	/**
	 * test that a heartbeat event is sent if no heartbeat date exists in zookeeper.
	 */
	@Test
	public void testHeartbeatEventNoHeartbeatDate() throws Exception {
		
		MockCallHomeEventManager manager = new MockCallHomeEventManager();
		LicenseInfoExt licenseInfo = new LicenseInfoExt();
		manager.setLicenseInfo(licenseInfo);

		Assert.assertTrue(manager.doSendHeartBeat(licenseInfo));
	}
	
	/**
	 * test that a heartbeat event is sent if no CallInfo object returned from zookeeper.
	 */
	@Test
	public void testHeartbeatEventNoCallInfoObject() throws Exception {

		MockCallHomeEventManager manager = new MockCallHomeEventManager();
		LicenseInfoExt licenseInfo = null;

		Assert.assertTrue(manager.doSendHeartBeat(licenseInfo));
	}
	
	
	/**
	 * Test case which recognizes that a heartbeat event should not be sent.
	 */
	@Test
	public void testHeartbeatEventFalse() throws Exception {
		
		MockCallHomeEventManager manager = new MockCallHomeEventManager();
		LicenseInfoExt licenseInfo = new LicenseInfoExt();
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, (CallHomeConstants.HEARTBEART_EVENT_THRESHOLD * -1) + 1);
		SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
		String stringDate = sdf.format(cal.getTime());
		
		
		licenseInfo.setLastHeartbeatEventDate(stringDate);
		manager.setLicenseInfo(licenseInfo);

		Assert.assertFalse(manager.doSendHeartBeat(licenseInfo));
	}
	
	/**
	 * Test case which recognizes that a license expiration event should be sent.
	 */
	@Test
	public void testLicenseExpirationEventTrue() throws Exception {
		
		MockCallHomeEventManager manager = new MockCallHomeEventManager();
		LicenseInfoExt licenseInfo = new LicenseInfoExt();
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, CallHomeConstants.LICENSE_EXPIRATION_EVENT_THRESHOLD * -1 );
		SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
		String stringDate = sdf.format(cal.getTime());
		
		
		licenseInfo.setLastLicenseExpirationDateEventDate(stringDate);
		manager.setLicenseInfo(licenseInfo);

		Assert.assertTrue(manager.doSendLicenseExpiration(licenseInfo));
	}
	
	/**
	 * test that a license expiration event is sent if no heartbeat date exists in zookeeper.
	 */
	@Test
	public void testLicenseExpirationEventNoExpirationDate() throws Exception {
		
		MockCallHomeEventManager manager = new MockCallHomeEventManager();
		LicenseInfoExt licenseInfo = new LicenseInfoExt();
		manager.setLicenseInfo(licenseInfo);
		
		Assert.assertTrue(manager.doSendLicenseExpiration(licenseInfo));
	}
	
	/**
	 * test that a license expiration event is sent if no CallInfo object returned from zookeeper.
	 */
	@Test
	public void testLicensExpirationEventNoCallInfoObject() throws Exception {
		
		MockCallHomeEventManager manager = new MockCallHomeEventManager();
		LicenseInfoExt licenseInfo = null;

		Assert.assertTrue(manager.doSendLicenseExpiration(licenseInfo));
	}
	
	
	/**
	 * Test case which recognizes that a license expiration event should not be sent.
	 */
	@Test
	public void testLicenExpirationEventFalse() throws Exception {
		
		MockCallHomeEventManager manager = new MockCallHomeEventManager();
		LicenseInfoExt licenseInfo = new LicenseInfoExt();
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, (CallHomeConstants.LICENSE_EXPIRATION_EVENT_THRESHOLD * -1) + 1);
		SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
		String stringDate = sdf.format(cal.getTime());
		
		
		licenseInfo.setLastLicenseExpirationDateEventDate(stringDate);
		manager.setLicenseInfo(licenseInfo);
		
		Assert.assertFalse(manager.doSendLicenseExpiration(licenseInfo));
	}
	
	/**
	 * Test case which recognizes that a registration event should be sent.
	 */
	@Test
	public void testRegistrationEventTrue() throws Exception {
		MockCallHomeEventManager manager = new MockCallHomeEventManager();
		LicenseInfoExt licenseInfo = new LicenseInfoExt();
		
		manager.setLicenseInfo(licenseInfo);
		
		Assert.assertTrue(manager.doSendRegistration(licenseInfo));
	
	}
	
	/**
	 * test that a registration event is sent if no CallInfo object returned from zookeeper.
	 */
	@Test
	public void testRegistrationEventNoCallInfoObject() throws Exception {
		
		MockCallHomeEventManager manager = new MockCallHomeEventManager();
		LicenseInfoExt licenseInfo = null;
		Assert.assertTrue(manager.doSendLicenseExpiration(licenseInfo));
	}
	
	/**
	 * Test case which recognizes that a registration event should not be sent.
	 */
	@Test
	public void testRegistrationEventFalse() throws Exception {
		MockCallHomeEventManager manager = new MockCallHomeEventManager();
		LicenseInfoExt licenseInfo = new LicenseInfoExt();
		SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
		String stringDate = sdf.format(new java.util.Date());
		licenseInfo.setLastRegistrationEventDate(stringDate);
		manager.setLicenseInfo(licenseInfo);
		
		Assert.assertFalse(manager.doSendRegistration(licenseInfo));
	}
}

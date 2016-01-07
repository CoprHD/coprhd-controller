/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 * @author mudit.jain@emc.com
 */

package com.emc.storageos.volumecontroller.impl.isilon;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.isilon.restapi.IsilonApiFactory;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicy;
import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;

public class IsilonFileStorageDeviceReplicationTest {
	private static IsilonFileStorageDevice _isi;
	private static StorageSystem _device;
	private static String ip = EnvConfig.get("sanity", "isilon.ip");
	private static String userName = EnvConfig.get("sanity", "isilon.username");
	private static String password = EnvConfig.get("sanity", "isilon.password");
	private static BiosCommandResult result;

	public static void setUp() {
		System.out.println("hh222222");
		_isi = new IsilonFileStorageDevice();
		IsilonApiFactory factory = new IsilonApiFactory();
		factory.init();
		_isi.setIsilonApiFactory(factory);
		_device = new StorageSystem();
		_device.setSystemType("isilon");
		_device.setIpAddress(ip);
		_device.setPortNumber(8080);
		_device.setUsername(userName);
		_device.setPassword(password);
	}

	public void testCreateReplicationPolicy() {
		result = _isi.doCreateReplicationPolicy(_device, "", "", "", "", IsilonSyncPolicy.Action.copy, "", "");
		result.getCommandStatus();
	}

/*	public static void main(String args[]) {
		System.out.println("zscascd");
	}*/

}

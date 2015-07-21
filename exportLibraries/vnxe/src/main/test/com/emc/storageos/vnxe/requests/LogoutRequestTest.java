/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.EnvConfig;


public class LogoutRequestTest {
	private static KHClient _client;
    private static String host = EnvConfig.get("sanity", "vnxe.host");
    private static String userName = EnvConfig.get("sanity", "vnxe.username");
    private static String password = EnvConfig.get("sanity", "vnxe.password");

	@BeforeClass
    public static void setup() throws Exception {
		synchronized (_client) {
		_client = new KHClient(host, userName, password);
	}
	
	}
	
	@Test
	public void logoutTest() {
		StorageSystemRequest reqSys = new StorageSystemRequest(_client);
		reqSys.get();
		LogoutRequest req = new LogoutRequest(_client);
		req.executeRequest();
		
		
	}

}

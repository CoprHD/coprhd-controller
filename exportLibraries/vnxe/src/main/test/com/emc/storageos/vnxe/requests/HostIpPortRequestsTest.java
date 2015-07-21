/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vnxe.models.HostIpPortCreateParam;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeHostIpPort;

public class HostIpPortRequestsTest {
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
	public void createHostIpPortTest() {
		HostIpPortRequests req = new HostIpPortRequests(_client);
	    HostIpPortCreateParam parm = new HostIpPortCreateParam();
	    parm.setAddress("10.247.87.208");
	    VNXeBase host = new VNXeBase();
	    host.setId("Host_1");
	    parm.setHost(host);
		VNXeCommandResult result = req.createHostIpPort(parm);
	    
	    String id = result.getId();
	    System.out.println(id);

	}
	
	//@Test
	public void getIpPortTest() {
		HostIpPortRequests req = new HostIpPortRequests(_client);
	    VNXeHostIpPort ipPort = req.getIpPortByIpAddress("1.1.1.1");
	    if (ipPort == null) {
	    	System.out.println("could not found the ip");
	    }
	}
}

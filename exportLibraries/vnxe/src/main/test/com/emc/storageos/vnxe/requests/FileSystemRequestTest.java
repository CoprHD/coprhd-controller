/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.requests;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vnxe.models.VNXeFileSystem;


public class FileSystemRequestTest {

	private static KHClient _client;
    private static String host = EnvConfig.get("sanity", "vnxe.host");
    private static String userName = EnvConfig.get("sanity", "vnxe.username");
    private static String password = EnvConfig.get("sanity", "vnxe.password");
	@BeforeClass
    public static void setup() throws Exception {
		_client = new KHClient(host, userName, password);
	}
	
	@Test
	public void getTest() {
		FileSystemRequest req = new FileSystemRequest(_client, "fs_5");
	    VNXeFileSystem fs= req.get();
	    
	    String name = fs.getName();
	    System.out.println(name);
	    String resourceId = fs.getStorageResource().getId();
	    System.out.println(resourceId);

	}
}

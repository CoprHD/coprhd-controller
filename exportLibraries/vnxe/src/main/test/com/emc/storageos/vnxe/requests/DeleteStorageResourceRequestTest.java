/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vnxe.models.VNXeCommandResult;

public class DeleteStorageResourceRequestTest {

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

    // @Test
    public void deleteFileSystem() {

        DeleteStorageResourceRequest req = new DeleteStorageResourceRequest(_client);
        VNXeCommandResult response = null;

        response = req.deleteFileSystemSync("fs_25", true);

        System.out.println(response.getSuccess());

    }

    @Test
    public void deleteLun() {

        DeleteStorageResourceRequest req = new DeleteStorageResourceRequest(_client);
        VNXeCommandResult response = null;

        response = req.deleteLunSync("sv_48", true);

        // System.out.println(response.getId());

    }

}

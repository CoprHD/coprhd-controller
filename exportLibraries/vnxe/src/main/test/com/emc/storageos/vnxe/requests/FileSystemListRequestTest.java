/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vnxe.models.VNXeFileSystem;

public class FileSystemListRequestTest {
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
    public void getTest() {
        FileSystemListRequest req = new FileSystemListRequest(_client);
        List<VNXeFileSystem> list = req.get();
        for (VNXeFileSystem fs : list) {
            String id = fs.getId();
            System.out.println(id);
        }

        VNXeFileSystem fs1 = req.getByStorageResource("res_19");
        System.out.println(fs1.getName());

        /*
         * VNXeFileSystem fs2 = req.getByFSName("ProviderTenant_fskh02_5bb3ac40-65f3-4ce1-b629-0c2f0775647c");
         * System.out.println(fs2.getId());
         */
    }
}
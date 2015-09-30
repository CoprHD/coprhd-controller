/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vnxe.models.VNXeNfsShare;

public class NfsShareRequestsTest {
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
    public void findNfsShareTest() {
        NfsShareRequests req = new NfsShareRequests(_client);
        VNXeNfsShare share = req.findNfsShare("fs_30", "ProviderTenant_fskh33_d7909c79-3bfe-4845-bc84-6c9775f1e44d-share" + host, "3.1.0");
        req.get();
        System.out.println(share.getId());

        /*
         * VNXeFileSystem fs2 = req.getByFSName("ProviderTenant_fskh02_5bb3ac40-65f3-4ce1-b629-0c2f0775647c");
         * System.out.println(fs2.getId());
         */
    }

}

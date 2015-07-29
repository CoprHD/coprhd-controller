/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vnxe.models.HostLun;

public class HostLunRequestsTest {
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
    public void findHostLunTest() {
        HostLunRequests req = new HostLunRequests(_client);
        HostLun hostLun = req.getHostLun("sv_1", "Host_4", HostLunRequests.ID_SEQUENCE_LUN);

        System.out.println(hostLun.getHlu());

    }

}

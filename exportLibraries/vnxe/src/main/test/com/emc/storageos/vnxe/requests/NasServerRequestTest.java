/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vnxe.models.VNXeNasServer;

public class NasServerRequestTest {
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
        NasServerRequest req = new NasServerRequest(_client, "system_nas_0");
        VNXeNasServer nasServer = req.get();

        String name = nasServer.getName();
        System.out.println(name);
        String spId = nasServer.getCurrentSP().getId();
        System.out.println(spId);
        System.out.println(nasServer.getMode());
    }

}

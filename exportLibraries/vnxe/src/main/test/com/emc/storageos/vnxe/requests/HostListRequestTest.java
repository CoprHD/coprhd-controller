/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vnxe.models.HostCreateParam;
import com.emc.storageos.vnxe.models.HostTypeEnum;
import com.emc.storageos.vnxe.models.VNXeCommandResult;

public class HostListRequestTest {
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
    public void createHostTest() {
        HostListRequest req = new HostListRequest(_client);
        HostCreateParam parm = new HostCreateParam();
        parm.setName("test-host-1");
        parm.setType(HostTypeEnum.HOSTMANUAL.getValue());
        VNXeCommandResult result = req.createHost(parm);

        String id = result.getId();
        System.out.println(id);

    }

}

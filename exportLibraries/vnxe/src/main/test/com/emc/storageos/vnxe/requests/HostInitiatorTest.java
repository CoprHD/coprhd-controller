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
import com.emc.storageos.vnxe.models.HostInitiatorCreateParam;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeHostInitiator;
import com.emc.storageos.vnxe.models.VNXeHostInitiator.HostInitiatorTypeEnum;

public class HostInitiatorTest {
    private static KHClient _client;
    private static String host = EnvConfig.get("sanity", "vnxe.host");
    private static String userName = EnvConfig.get("sanity", "vnxe.username");
    private static String password = EnvConfig.get("sanity", "vnxe.password");
    @BeforeClass
    public static void setup() throws Exception {
    	_client = new KHClient(host, userName, password);
    }
    
    //@Test
    public void createHostInititaor() {
        String hostId = "Host_1";
        String wwn = "20:00:00:25:b5:5d:00:04:20:00:00:25:b5:5d:00:e5";

        HostInitiatorCreateParam initCreateParam = new HostInitiatorCreateParam();
        VNXeBase host = new VNXeBase(hostId);
        initCreateParam.setHost(host);
        initCreateParam.setInitiatorType(HostInitiatorTypeEnum.INITIATOR_TYPE_FC.getValue());
        initCreateParam.setInitiatorWWNorIqn(wwn);
        HostInitiatorRequest req = new HostInitiatorRequest(_client);
        req.createHostInitiator(initCreateParam);
    }
    
    @Test
    public void getHostInitiator() {
        String wwn = "20:00:00:25:b5:5d:00:04:20:00:00:25:b5:5d:00:e5";
        HostInitiatorRequest req = new HostInitiatorRequest(_client);
        VNXeHostInitiator init = req.getByIQNorWWN(wwn);
        System.out.println(init.getPortWWN());
    }
}

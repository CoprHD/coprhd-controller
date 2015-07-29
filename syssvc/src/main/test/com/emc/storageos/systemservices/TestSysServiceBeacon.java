/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices;

import java.util.List;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.CoordinatorTestBase;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.systemservices.impl.SysSvcBeaconImpl;
import com.emc.storageos.systemservices.impl.SysSvcImpl;

/**
 * 
 */
public class TestSysServiceBeacon extends CoordinatorTestBase {
    private static final String SERVICE_BEAN = "syssvcserver";
    private static final String SERVICE_INFO = "serviceinfo";
    private static final String BEACON_BEAN = "beacon";

    @Test
    @Ignore("This references a configuration that doesn't exist (syssvc-config.xml), either fix or delete this test")
    public void testBeacon() throws Exception {
        String curVersion = "current_version";

        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("/syssvc-config.xml");
        SysSvcImpl sysservice = (SysSvcImpl) ctx.getBean(SERVICE_BEAN);
        sysservice.start();
        ServiceImpl svc = (ServiceImpl) ctx.getBean(SERVICE_INFO);
        CoordinatorClient client = connectClient();
        SysSvcBeaconImpl beacon = (SysSvcBeaconImpl) ctx.getBean(BEACON_BEAN);

        List<Service> found = client.locateAllServices(svc.getName(), svc.getVersion(), (String) null, null);
        Assert.assertNotNull(found);
        Assert.assertEquals(found.size(), 1);
        Service first = found.get(0);
        Assert.assertEquals(first.getId(), svc.getId());
        Assert.assertEquals(first.getEndpoint(), svc.getEndpoint());
        Assert.assertEquals(first.getAttribute(curVersion), null);

        svc.setAttribute(curVersion, "2");
        beacon.publish();

        found = client.locateAllServices(svc.getName(), svc.getVersion(), (String) null, null);
        Assert.assertNotNull(found);
        Assert.assertEquals(found.size(), 1);
        first = found.get(0);
        Assert.assertEquals(first.getId(), svc.getId());
        Assert.assertEquals(first.getEndpoint(), svc.getEndpoint());
        Assert.assertEquals(first.getAttribute(curVersion), "2");

        sysservice.stop();
    }
}

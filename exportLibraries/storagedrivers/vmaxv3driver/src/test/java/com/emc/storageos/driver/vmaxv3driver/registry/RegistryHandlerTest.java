/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.registry;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.impl.InMemoryRegistryImpl;

/**
 * Test class for "RegistryHandler".
 *
 * Created by gang on 7/26/16.
 */
public class RegistryHandlerTest {

    private String arrayId = "12345";
    private String scheme = "https";
    private String host = "10.247.98.74";
    private Integer port = 8443;
    private String user = "smc";
    private String password = "smc";
    private Integer updatedPort = 9999;
    private String updateUser = "root";

    @Test
    public void testAccessInfo() {
        Registry registry = new InMemoryRegistryImpl();
        RegistryHandler handler = new RegistryHandler(registry);
        // Write, read and validate.
        handler.setAccessInfo(arrayId, scheme, host, port, user, password);
        AccessInfo info= handler.getAccessInfo(arrayId);
        Assert.assertEquals(info.getScheme(), scheme);
        Assert.assertEquals(info.getHost(), host);
        Assert.assertEquals(info.getPort(), port);
        Assert.assertEquals(info.getUsername(), user);
        Assert.assertEquals(info.getPassword(), password);
        // Update, read and validate.
        handler.setAccessInfo(arrayId, scheme, host, updatedPort, updateUser, password);
        info= handler.getAccessInfo(arrayId);
        Assert.assertEquals(info.getScheme(), scheme);
        Assert.assertEquals(info.getHost(), host);
        Assert.assertEquals(info.getPort(), updatedPort);
        Assert.assertEquals(info.getUsername(), updateUser);
        Assert.assertEquals(info.getPassword(), password);
    }
}

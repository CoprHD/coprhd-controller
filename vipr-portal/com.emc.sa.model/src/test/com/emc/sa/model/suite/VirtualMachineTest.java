/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.suite;

import java.net.URI;
import java.util.List;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.emc.sa.model.DBClientTestBase;
import com.emc.storageos.db.client.model.uimodels.VirtualMachine;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;

public class VirtualMachineTest extends DBClientTestBase {

    private static final Logger _logger = Logger.getLogger(VirtualMachineTest.class);

    @Test
    public void testPersistObject() throws Exception {
        _logger.info("Starting persist VirtualMachine test");

        ModelClient modelClient = getModelClient();

        VirtualMachine model = new VirtualMachine();
        model.setId(URIUtil.createId(VirtualMachine.class));
        model.setLabel("foo");
        model.setRunning(false);
        model.setTemplate(true);
        URI datacenterUri = URIUtil.createId(VcenterDataCenter.class);
        NamedURI datacenterId = new NamedURI(datacenterUri, "dc1");
        model.setDatacenterId(datacenterId);

        modelClient.save(model);

        model = modelClient.virtualMachines().findById(model.getId());

        Assert.assertNotNull(model);
        Assert.assertEquals("foo", model.getLabel());
        Assert.assertEquals(false, model.getRunning());
        Assert.assertEquals(true, model.getTemplate());
        Assert.assertEquals(datacenterId, model.getDatacenterId());

    }

    @Test
    public void testFindByDatacenter() throws Exception {
        _logger.info("Starting findByDatacenter test");

        ModelClient modelClient = getModelClient();

        Vcenter v2 = createVCenterWithLabel("v2");
        modelClient.save(v2);

        VcenterDataCenter dc2 = createDatacenterWithLabel("dc2");
        dc2.setVcenter(v2.getId());
        modelClient.save(dc2);

        VirtualMachine vm1 = createWithLabel("vm1");
        vm1.setDatacenterId(new NamedURI(dc2.getId(), dc2.getLabel()));
        modelClient.save(vm1);

        VcenterDataCenter dc3 = createDatacenterWithLabel("dc3");
        dc3.setVcenter(v2.getId());
        modelClient.save(dc3);

        VirtualMachine vm2 = createWithLabel("vm2");
        vm2.setDatacenterId(new NamedURI(dc3.getId(), dc3.getLabel()));
        modelClient.save(vm2);

        VirtualMachine vm3 = createWithLabel("vm3");
        vm3.setDatacenterId(new NamedURI(dc3.getId(), dc3.getLabel()));
        modelClient.save(vm3);

        List<VirtualMachine> virtualMachines = modelClient.virtualMachines().findByDatacenter(dc2.getId());
        Assert.assertNotNull(virtualMachines);
        Assert.assertEquals(1, virtualMachines.size());

        virtualMachines = modelClient.virtualMachines().findByDatacenter(dc3.getId());
        Assert.assertNotNull(virtualMachines);
        Assert.assertEquals(2, virtualMachines.size());

    }

    private Vcenter createVCenterWithLabel(String label) {
        Vcenter model = new Vcenter();
        model.setId(URIUtil.createId(Vcenter.class));
        model.setLabel(label);
        model.setIpAddress("my hostname");
        model.setPassword("my password");
        model.setPortNumber(42);
        model.setUseSSL(true);
        model.setUsername("my username");
        return model;
    }

    private VcenterDataCenter createDatacenterWithLabel(String label) {
        VcenterDataCenter model = new VcenterDataCenter();
        model.setId(URIUtil.createId(VcenterDataCenter.class));
        model.setLabel(label);
        return model;
    }

    private VirtualMachine createWithLabel(String label) {
        VirtualMachine model = new VirtualMachine();
        model.setId(URIUtil.createId(VirtualMachine.class));
        model.setLabel(label);
        model.setRunning(false);
        model.setTemplate(true);
        return model;
    }

}

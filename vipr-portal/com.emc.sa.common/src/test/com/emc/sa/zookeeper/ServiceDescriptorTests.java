/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.zookeeper;

import com.emc.sa.descriptor.ServiceDefinition;
import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceDefinitionLoader;
import com.emc.sa.descriptor.ServiceField;
import com.emc.sa.util.TestCoordinatorService;

import org.junit.*;

import java.util.List;
import java.util.Locale;

// Suite requires coordinator be running on localhost, which will not be the case on build servers running the "test" rule.
// Changes could be made to run a self-contained coordinator.  See DbServiceTestBase for examples
@Ignore
public class ServiceDescriptorTests {
    private TestCoordinatorService coordinatorService;

    @Before
    public void setup() throws Exception {
        coordinatorService = new TestCoordinatorService();
        coordinatorService.startClean();
    }

    @Test
    public void testLoadingAndRetrieving() throws Exception {
        ZkServiceDescriptors serviceDescriptors = new ZkServiceDescriptors();
        serviceDescriptors.setCoordinatorClient(coordinatorService.getCoordinatorClient());
        serviceDescriptors.start();

        List<ServiceDefinition> services = ServiceDefinitionLoader.load(ServiceDescriptorTests.class.getClassLoader());
        Assert.assertEquals(1, services.size());

        serviceDescriptors.addServices(services);

        ServiceDescriptor descriptor = serviceDescriptors.getDescriptor(Locale.getDefault(), "TestService");
        Assert.assertNotNull(descriptor);
        Assert.assertEquals("TestService", descriptor.getServiceId());
        List<ServiceField> fields = descriptor.getFieldList();
        Assert.assertEquals(3, fields.size());
        Assert.assertEquals("project", fields.get(0).getName());
        Assert.assertEquals("Project", fields.get(0).getLabel());
    }

    @After
    public void tearDown() throws Exception {
        coordinatorService.stop();
    }
}

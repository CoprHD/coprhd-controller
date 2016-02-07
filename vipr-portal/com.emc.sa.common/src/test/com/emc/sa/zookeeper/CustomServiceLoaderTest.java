package com.emc.sa.zookeeper;

import java.util.List;
import java.util.Locale;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.sa.descriptor.ServiceDefinition;
import com.emc.sa.descriptor.ServiceDefinitionLoader;
import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceField;
import com.emc.sa.util.TestCoordinatorService;

public class CustomServiceLoaderTest {
	
	
    private TestCoordinatorService coordinatorService;


    public void setup() throws Exception {
        coordinatorService = new TestCoordinatorService();
        coordinatorService.startClean();
    }

    public void testLoadingAndRetrieving() throws Exception {
        ZkServiceDescriptors serviceDescriptors = new ZkServiceDescriptors();
        serviceDescriptors.setCoordinatorClient(coordinatorService.getCoordinatorClient());
        serviceDescriptors.start();

        List<ServiceDefinition> services = ServiceDefinitionLoader.load(ServiceDescriptorTests.class.getClassLoader());
//        Assert.assertEquals(1, services.size());

        serviceDescriptors.addServices(services);

        ServiceDescriptor descriptor = serviceDescriptors.getDescriptor(Locale.getDefault(), "TestService");
        Assert.assertNotNull(descriptor);
//        Assert.assertEquals("TestService", descriptor.getServiceId());
//        List<ServiceField> fields = descriptor.getFieldList();
//        Assert.assertEquals(3, fields.size());
//        Assert.assertEquals("project", fields.get(0).getName());
//        Assert.assertEquals("Project", fields.get(0).getLabel());
    }

    public void tearDown() throws Exception {
        coordinatorService.stop();
    }

    public static void main(String[] args) throws Exception {
    	CustomServiceLoaderTest customServiceLoaderTest =new CustomServiceLoaderTest();
    	
    	customServiceLoaderTest.setup();
    	
    	customServiceLoaderTest.testLoadingAndRetrieving();
    	
    	customServiceLoaderTest.tearDown();
    	
    	
    	
	}

}

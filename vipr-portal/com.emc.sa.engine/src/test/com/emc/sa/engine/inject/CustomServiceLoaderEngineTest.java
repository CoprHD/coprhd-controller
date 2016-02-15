package com.emc.sa.engine.inject;

import java.util.List;
import java.util.Locale;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.sa.catalog.ExtentionClassLoader;
import com.emc.sa.descriptor.ServiceDefinition;
import com.emc.sa.descriptor.ServiceDefinitionLoader;
import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceField;
import com.emc.sa.engine.service.DefaultExecutionServiceFactory;
import com.emc.sa.util.TestCoordinatorService;
import com.emc.sa.zookeeper.ServiceDescriptorTests;
import com.emc.sa.zookeeper.ZkServiceDescriptors;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.Order;

public class CustomServiceLoaderEngineTest {
	
	
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
    	CustomServiceLoaderEngineTest customServiceLoaderTest =new CustomServiceLoaderEngineTest();
    	
    	customServiceLoaderTest.setup();
    	
    	customServiceLoaderTest.testLoadingAndRetrieving();
    	
    	Object obj2 = ExtentionClassLoader.getProxyObject("com.emc.sa.service.vipr.plugins.tasks.CustomSample");
    	System.out.println("Messge from extension loader "+obj2.getClass().getCanonicalName());
    	
    	DefaultExecutionServiceFactory def = new DefaultExecutionServiceFactory();
    	Order order = new Order();
    	CatalogService catalogService = new CatalogService();
    	catalogService.setBaseService("CustomSample@Extension");
    	def.createService(order, catalogService);
    	
    	customServiceLoaderTest.tearDown();
    	

    	
    	
	}

}

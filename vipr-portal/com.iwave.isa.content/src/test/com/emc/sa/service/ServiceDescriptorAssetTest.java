/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.emc.sa.asset.AssetOptionsManagerImpl;
import com.emc.sa.asset.AssetOptionsProvider;
import com.emc.sa.descriptor.AbstractServiceDescriptors;
import com.emc.sa.descriptor.ServiceDefinition;
import com.emc.sa.descriptor.ServiceDefinitionLoader;
import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceField;

/**
 * Test for verifying all the types in all service descriptors are valid.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({ "ServiceDescriptorAssetTest.xml" })
public class ServiceDescriptorAssetTest {
    @Autowired
    private AssetOptionsManagerImpl assetManager;

    @Test
    public void testAssetManager() throws IOException {
        TestServiceDescriptors serviceDescriptors = new TestServiceDescriptors();
        for (ServiceDescriptor descriptor : serviceDescriptors.listDescriptors(Locale.getDefault())) {
            verifyAssetFields(descriptor);
        }
    }

    private void verifyAssetFields(ServiceDescriptor descriptor) {
        Set<String> allAssetTypes = descriptor.getAllAssetTypes();
        for (ServiceField field : descriptor.getAllFieldList()) {
            if (!field.isAsset()) {
                continue;
            }
            String assetType = field.getAssetType();
            AssetOptionsProvider provider = assetManager.getProviderForAssetType(assetType);
            if (provider == null) {
                Assert.fail(String.format("%s [%s]: No provider found for type: %s", descriptor.getTitle(),
                        field.getName(), assetType));
            }
            
            try {
                List<String> assetDependencies = provider.getAssetDependencies(assetType, allAssetTypes);
                for (String assetDependency : assetDependencies) {
                    AssetOptionsProvider dependencyProvider = assetManager.getProviderForAssetType(assetDependency);
                    if (dependencyProvider == null) {
                        Assert.fail(String.format("%s [%s]: No provider found for dependency '%s' of type: %s",
                                descriptor.getTitle(), field.getName(), assetDependency, assetType));
                    }
                }
            }
            catch (IllegalStateException e) {
                Assert.fail(String.format("%s [%s]: Could not query dependencies of type: %s, provided: %s",
                        descriptor.getTitle(), field.getName(), assetType, allAssetTypes));
            }
        }
    }

    private static class TestServiceDescriptors extends AbstractServiceDescriptors {
        Map<String, ServiceDefinition> definitions;

        public TestServiceDescriptors() throws IOException {
            definitions = new HashMap<>();
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            for (ServiceDefinition definition : ServiceDefinitionLoader.load(cl)) {
                definitions.put(definition.serviceId, definition);
            }
        }

        @Override
        protected Collection<ServiceDefinition> getServiceDefinitions() {
            return definitions.values();
        }

        @Override
        protected ServiceDefinition getServiceDefinition(String serviceId) {
            return definitions.get(serviceId);
        }
    }
}

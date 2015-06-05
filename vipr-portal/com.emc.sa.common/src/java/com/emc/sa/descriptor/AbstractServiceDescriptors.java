/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public abstract class AbstractServiceDescriptors implements ServiceDescriptors {
    private String[] bundleNames = { "com.emc.sa.descriptor.ServiceDescriptors" };

    protected abstract Collection<ServiceDefinition> getServiceDefinitions();

    protected abstract ServiceDefinition getServiceDefinition(String serviceId);

    public String[] getBundleNames() {
        return bundleNames;
    }

    public void setBundleNames(String[] bundleNames) {
        this.bundleNames = bundleNames;
    }

    protected ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    protected ServiceDescriptorBuilder createBuilder(Locale locale) {
        return new ServiceDescriptorBuilder(getClassLoader(), locale, getBundleNames());
    }

    @Override
    public Collection<ServiceDescriptor> listDescriptors(Locale locale) {
        ServiceDescriptorBuilder builder = createBuilder(locale);
        List<ServiceDescriptor> descriptors = new ArrayList<>();
        for (ServiceDefinition serviceDef : getServiceDefinitions()) {
            ServiceDescriptor descriptor = builder.build(serviceDef);
            descriptors.add(descriptor);
        }
        return descriptors;
    }

    @Override
    public ServiceDescriptor getDescriptor(Locale locale, String serviceId) {
        ServiceDefinition serviceDef = getServiceDefinition(serviceId);
        if (serviceDef != null) {
            ServiceDescriptorBuilder builder = createBuilder(locale);
            return builder.build(serviceDef);
        }
        else {
            return null;
        }
    }
}
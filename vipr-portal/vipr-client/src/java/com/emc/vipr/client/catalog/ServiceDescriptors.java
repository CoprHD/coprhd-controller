/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import java.util.List;

import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.catalog.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.ServiceDescriptorList;
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep;

public class ServiceDescriptors {

    protected final ViPRCatalogClient2 parent;
    protected final RestClient client;

    public ServiceDescriptors(ViPRCatalogClient2 parent, RestClient client) {
        this.parent = parent;
        this.client = client;
    }

    public List<ServiceDescriptorRestRep> getServiceDescriptors() {
        ServiceDescriptorList response = client.get(ServiceDescriptorList.class, PathConstants.SERVICE_DESCRIPTORS_URL);
        return response.getServiceDescriptors();
    }

    public ServiceDescriptorRestRep getServiceDescriptor(CatalogServiceRestRep catalogService) {
        if (catalogService == null) {
            return null;
        }
        return getServiceDescriptor(catalogService.getBaseService());
    }

    public ServiceDescriptorRestRep getServiceDescriptor(String name) {
        return client.get(ServiceDescriptorRestRep.class, PathConstants.SERVICE_DESCRIPTOR_NAME_URL, name);
    }

}

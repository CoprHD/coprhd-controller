/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api;

import static com.emc.sa.api.mapper.ServiceDescriptorMapper.map;

import java.util.List;
import java.util.Locale;
import java.util.Collection;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.catalog.ServiceDescriptorUtil;
import com.emc.sa.catalog.WorkflowServiceDescriptor;
import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceDescriptors;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.vipr.model.catalog.ServiceDescriptorList;
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep;
import com.google.common.collect.Lists;

@DefaultPermissions(
        readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN },
        writeRoles = { Role.TENANT_ADMIN },
        readAcls = { ACL.ANY })
@Path("/catalog/service-descriptors")
public class ServiceDescriptorService extends CatalogResourceService {

    @Autowired
    private ServiceDescriptors serviceDescriptors;

    @Autowired
    private WorkflowServiceDescriptor workflowServiceDescriptor;

    /**
     * List service descriptors
     *
     * @prereq none
     * @brief List service descriptors
     * @return List of service descriptors
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("")
    public ServiceDescriptorList getServiceDescriptors() {
        Collection<ServiceDescriptor> descriptors = this.serviceDescriptors.listDescriptors(Locale.getDefault());
        descriptors.addAll(workflowServiceDescriptor.listDescriptors());

        List<ServiceDescriptorRestRep> serviceDescriptors = Lists.newArrayList();
        for (ServiceDescriptor descriptor : descriptors) {
            serviceDescriptors.add(map(descriptor));
        }

        ServiceDescriptorList serviceDescriptorList = new ServiceDescriptorList(serviceDescriptors);
        return serviceDescriptorList;
    }

    /**
     * Retrieve service descriptors
     *
     * @prereq none
     * @brief Retrieve service descriptor
     * @return Service descriptor
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{serviceId}")
    public ServiceDescriptorRestRep getServiceDescriptor(@PathParam("serviceId") String serviceId) {
        ServiceDescriptor descriptor = ServiceDescriptorUtil.getServiceDescriptorByName(serviceDescriptors, workflowServiceDescriptor, serviceId);
        return map(descriptor);
    }
}

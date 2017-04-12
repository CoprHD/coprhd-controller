/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.catalog

import static com.emc.vipr.sanity.Sanity.*
import static org.junit.Assert.*

import com.emc.vipr.model.catalog.ServiceDescriptorRestRep


class ServiceDescriptorServiceHelper {

    static void serviceDescriptorServiceTest() {

        println "  ## Service Descriptor Test ## "

        println "Getting all service descriptors"
        List<ServiceDescriptorRestRep> sds =
                catalog.serviceDescriptors().getServiceDescriptors();

        println ""

        assertNotNull(sds);
        assertEquals(Boolean.TRUE, sds.size() > 0);

        println "Getting name of a service descriptor"
        println ""

        String name = null;
        ServiceDescriptorRestRep sd = sds.get(0);

        if (sd != null && sd.getServiceId() != null) {
            name = sd.getServiceId();
        }

        println "Listing service descriptor by name"
        println ""

        ServiceDescriptorRestRep sdByName =
                catalog.serviceDescriptors().getServiceDescriptor(name);

        assertNotNull(sdByName);
        assertEquals(name, , sdByName.getServiceId());

        //TODO: add call to test GET based on catalog service

    }
}

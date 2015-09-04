/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "service_descriptors")
public class ServiceDescriptorList {

    private List<ServiceDescriptorRestRep> serviceDescriptors;

    public ServiceDescriptorList() {
    }

    public ServiceDescriptorList(List<ServiceDescriptorRestRep> serviceDescriptors) {
        this.serviceDescriptors = serviceDescriptors;
    }

    /**
     * List of service descriptors
     * 
     * @valid none
     */
    @XmlElement(name = "service_descriptor")
    public List<ServiceDescriptorRestRep> getServiceDescriptors() {
        if (serviceDescriptors == null) {
            serviceDescriptors = new ArrayList<>();
        }
        return serviceDescriptors;
    }

    public void setServiceDescriptors(List<ServiceDescriptorRestRep> serviceDescriptors) {
        this.serviceDescriptors = serviceDescriptors;
    }

}

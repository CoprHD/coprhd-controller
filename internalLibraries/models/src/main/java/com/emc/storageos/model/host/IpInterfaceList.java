/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Response for getting a list of host ip interfaces
 */
@XmlRootElement(name = "ip_interfaces")
public class IpInterfaceList {
    private List<NamedRelatedResourceRep> ipInterfaces;

    public IpInterfaceList() {
    }

    public IpInterfaceList(List<NamedRelatedResourceRep> ipInterfaces) {
        this.ipInterfaces = ipInterfaces;
    }

    /**
     * List of IPv4 or IPv6 interfaces of a host that exists in ViPR.
     * 
     * @valid none
     */
    @XmlElement(name = "ip_interface")
    public List<NamedRelatedResourceRep> getIpInterfaces() {
        if (ipInterfaces == null) {
            ipInterfaces = new ArrayList<NamedRelatedResourceRep>();
        }
        return ipInterfaces;
    }

    public void setIpInterfaces(List<NamedRelatedResourceRep> ipInterfaces) {
        this.ipInterfaces = ipInterfaces;
    }
}

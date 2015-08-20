/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_ip_interfaces")
public class IpInterfaceBulkRep extends BulkRestRep {

    private List<IpInterfaceRestRep> ipInterfaces;

    /**
     * List of IPv4 or IPv6 interfaces of a host that exists in ViPR.
     * 
     * @valid none
     */
    @XmlElement(name = "ip_interface")
    public List<IpInterfaceRestRep> getIpInterfaces() {
        if (ipInterfaces == null) {
            ipInterfaces = new ArrayList<IpInterfaceRestRep>();
        }
        return ipInterfaces;
    }

    public void setIpInterfaces(List<IpInterfaceRestRep> ipInterfaces) {
        this.ipInterfaces = ipInterfaces;
    }

    public IpInterfaceBulkRep() {
    }

    public IpInterfaceBulkRep(List<IpInterfaceRestRep> ipInterfaces) {
        this.ipInterfaces = ipInterfaces;
    }
}

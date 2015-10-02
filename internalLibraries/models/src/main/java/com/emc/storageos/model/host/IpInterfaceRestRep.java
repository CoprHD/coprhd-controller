/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * REST Response representing an Initiator.
 */
@XmlRootElement(name = "ip_interface")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class IpInterfaceRestRep extends HostInterfaceRestRep {

    private String ipAddress;
    private String netmask;
    private Integer prefixLength;
    private String scopeId;
    private String name;

    public IpInterfaceRestRep() {
    }

    public IpInterfaceRestRep(String ipAddress, String netmask,
            Integer prefixLength, String scopeId, String name) {
        this.ipAddress = ipAddress;
        this.netmask = netmask;
        this.prefixLength = prefixLength;
        this.scopeId = scopeId;
        this.name = name;
    }

    /**
     * The IPv4 or IPv6 address of this interface.
     * 
     */
    @XmlElement(name = "ip_address")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * Gets the netmask of an IPv4 address expressed as the
     * integer prefix in an IPv4 address CIDR notation.
     * For example 24 for 255.255.255.0 and 16 for
     * 255.255.0.0 etc..
     * 
     * @return the netmask of an IP interface
     */
    @XmlElement(name = "netmask")
    public String getNetmask() {
        return netmask;
    }

    public void setNetmask(String netmask) {
        this.netmask = netmask;
    }

    /**
     * Returns the prefix length for an IPv6 interface
     */
    @XmlElement(name = "prefix_length")
    public Integer getPrefixLength() {
        return prefixLength;
    }

    public void setPrefixLength(Integer prefixLength) {
        this.prefixLength = prefixLength;
    }

    /**
     * Gets the scope id for an IPv6 interface.
     * 
     * @return the scope id for an IPv6 interface
     */
    @XmlElement(name = "scope_id")
    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    /**
     * Gets the name of the interface
     * 
     * @return the name of the interface
     */
    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

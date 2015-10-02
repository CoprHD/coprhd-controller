/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.vcenter;

import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.host.ComputeSystemRestRep;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * REST Response representing an vCenter.
 */
@XmlRootElement(name = "vcenter")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class VcenterRestRep extends ComputeSystemRestRep {
    private String username;
    private String ipAddress;
    private Integer portNumber;
    private Boolean useSsl;
    private String osVersion;
    private Boolean cascadeTenancy;

    public VcenterRestRep() {
    }

    public VcenterRestRep(String username, String ipAddress,
            Integer portNumber, Boolean useSsl) {
        super();
        this.username = username;
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
        this.useSsl = useSsl;
    }

    public VcenterRestRep(RelatedResourceRep tenant, String username,
            String ipAddress, Integer portNumber, Boolean useSsl) {
        super(tenant);
        this.username = username;
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
        this.useSsl = useSsl;
    }

    /**
     * The login account name
     * 
     * @return the login account name
     */
    @XmlElement(name = "user_name")
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * The vCenter server management IP address
     * 
     * @return the vCenter server management IP address
     */
    @XmlElement(name = "ip_address")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * The vCenter server management port
     * 
     * @return the the vCenter server management port
     */
    @XmlElement(name = "port_number")
    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    /**
     * A flag which indicates whether SSL should be used when communicating with the vCenter
     * 
     * @return true if SSL should be used when communicating with the vCenter
     */
    @XmlElement(name = "use_ssl")
    public Boolean getUseSsl() {
        return useSsl;
    }

    public void setUseSsl(Boolean useSsl) {
        this.useSsl = useSsl;
    }

    /**
     * The operating system version of the vcenter.
     * 
     */
    @XmlElement(name = "os_version")
    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    /**
     * A flag indicating whether to cascade the vCenter tenancy to all its
     * datacenters and its clusters and hosts or not. If cascaded vCenter
     * can belong to only one tenant.
     *
     *                  and its hosts and clusters.
     */
    @XmlElement(name = "cascade_tenancy")
    public Boolean getCascadeTenancy() {
        return cascadeTenancy;
    }

    public void setCascadeTenancy(Boolean cascadeTenancy) {
        this.cascadeTenancy = cascadeTenancy;
    }

}

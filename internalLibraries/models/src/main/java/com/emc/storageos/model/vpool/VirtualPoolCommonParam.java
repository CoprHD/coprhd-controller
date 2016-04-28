/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import org.codehaus.jackson.annotate.JsonProperty;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.util.Set;

import com.emc.storageos.model.valid.Length;

/**
 * Parameter to VirtualPool creation
 */
public class VirtualPoolCommonParam {

    private String name;
    private String description;
    private Set<String> protocols;
    private Set<String> varrays;
    private Boolean useMatchedPools = true;
    private String provisionType;
    private String systemType;
    private Boolean longTermRetention;

    public VirtualPoolCommonParam() {
    }

    /**
     * The name for the virtual pool.
     * 
     */
    @XmlElement(required = false)
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The description for the virtual pool.
     * 
     */
    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElementWrapper(name = "protocols", required = true)
    /**
     * The set of supported protocols for the virtual pool. 
     * Valid values:
     *  FC = Fibre Channel (block)
     *  ISCSI = Internet Small Computer System Interface (block)
     *  FCoE = Fibre Channel over Ethernet (block)
     *  NFS = Network File System( file)
     *  NFSV4 = Network File System Version 4(file)
     *  CIFS = Common Internet File Systemm (file)
     * 
     */
    @XmlElement(name = "protocol", required = true)
    public Set<String> getProtocols() {
        return protocols;
    }

    public void setProtocols(Set<String> protocols) {
        this.protocols = protocols;
    }

    @XmlElementWrapper(name = "varrays")
    /**
     * The virtual arrays for the virtual pool
     * 
     */
    @XmlElement(name = "varray")
    @JsonProperty("varrays")
    public Set<String> getVarrays() {
        // TODO: empty collection workaround
        // if (varrays == null) {
        // varrays = new HashSet<String>();
        // }
        return varrays;
    }

    public void setVarrays(Set<String> varrays) {
        this.varrays = varrays;
    }

    /**
     * Determines if matched or valid assigned pools are returned from
     * command to retrieve the list of storage pools.
     * 
     */
    @XmlElement(name = "use_matched_pools")
    public Boolean getUseMatchedPools() {
        return useMatchedPools;
    }

    public void setUseMatchedPools(Boolean useMatchedPools) {
        this.useMatchedPools = useMatchedPools;
    }

    /**
     * The provisioning type for the virtual pool.
     * Valid values:
     *  NONE
     *  Thin
     *  Thick
     */
    @XmlElement(name = "provisioning_type", required = true)
    public String getProvisionType() {
        return provisionType;
    }

    public void setProvisionType(String provisionType) {
        this.provisionType = provisionType;
    }

    /**
     * The supported system type for the virtual pool.
     * Valid values:
     *  NONE
     *  vnxblock (block)
     *  vmax (block)
     *  openstack (block)
     *  vnxfile (file)
     *  isilon (file)
     *  netapp (file)
     */
    @XmlElement(name = "system_type")
    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    @XmlElement(name = "long_term_retention")
    public Boolean getLongTermRetention() {
        return longTermRetention;
    }

    public void setLongTermRetention(Boolean longTermRetention) {
        this.longTermRetention = longTermRetention;
    }

}

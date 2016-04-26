/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import org.codehaus.jackson.annotate.JsonProperty;
import javax.xml.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@XmlRootElement(name = "vpool")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class VirtualPoolCommonRestRep extends DataObjectRestRep {
    private String type;
    private String description;
    private Set<String> protocols;
    private String provisioningType;
    private Integer maxPaths;
    private Integer numResources;
    private String systemType;
    private List<RelatedResourceRep> varrays;
    private Boolean useMatchedPools;
    private List<RelatedResourceRep> assignedStoragePools;
    private List<RelatedResourceRep> matchedStoragePools;
    private List<RelatedResourceRep> invalidMatchedStoragePools;

    /**
     * Virtual pool storage resource type.
     * Valid values:
     *  block = Volume
     *  file = File System
     *  object = Object Store
     * 
     */
    @XmlElement
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * 
     * User defined description for this virtual pool.
     * 
     */
    @XmlElement
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Storage type provisioned for this virtual pool.
     * 
     */
    @XmlElement(name = "provisioning_type")
    public String getProvisioningType() {
        return provisioningType;
    }

    public void setProvisioningType(String provisioningType) {
        this.provisioningType = provisioningType;
    }

    @XmlElementWrapper(name = "protocols")
    /**
     * The set of supported protocols for the virtual pool.
     * Valid values:
     *  FC = Fibre Channel (block)
     *  ISCSI = Internet Small Computer System Interface (block)
     *  FCoE = Fibre Channel over Ethernet (block)
     *  NFS = Network File System (file)
     *  NFSV4 = Network File System Version 4 (file)
     *  CIFS = Common Internet File System (file)
     * 
     */
    @XmlElement(name = "protocol")
    public Set<String> getProtocols() {
        if (protocols == null) {
            protocols = new LinkedHashSet<String>();
        }
        return protocols;
    }

    public void setProtocols(Set<String> protocols) {
        this.protocols = protocols;
    }

    /**
     * The supported system type for the virtual pool. 
     * Valid values:
     *  NONE
     *  vnxblock (Block)
     *  vmax (Block)
     *  vnxfile (File)
     *  isilon (File)
     *  netapp (File)
     */
    @XmlElement(name = "system_type")
    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    /**
     * @deprecated use getMaxPaths from BlockVirtualPoolRestRep instead of getNumPaths
     * @see BlockVirtualPoolRestRep#getMaxPaths()
     *      TODO: Remove deprecated API calls in next major release
     */
    @Deprecated
    @XmlElement(name = "num_paths")
    public Integer getNumPaths() {
        return maxPaths;
    }

    /**
     * @deprecated use setMaxPaths from BlockVirtualPoolRestRep instead of setNumPaths
     * @see BlockVirtualPoolRestRep#setMaxPaths(Integer)
     *      TODO: Remove deprecated API calls in next major release
     */
    @Deprecated
    public void setNumPaths(Integer numPaths) {
        this.maxPaths = numPaths;
    }

    /**
     * Number of resources provisioned to this ViPR using this
     * virtual pool.
     * 
     */
    @XmlElement(name = "num_resources")
    public Integer getNumResources() {
        return numResources;
    }

    public void setNumResources(Integer numResources) {
        this.numResources = numResources;
    }

    @XmlElementWrapper(name = "varrays")
    /**
     * The virtual arrays assigned to this virtual pool.
     * 
     */
    @XmlElement(name = "varray")
    @JsonProperty("varrays")
    public List<RelatedResourceRep> getVirtualArrays() {
        if (varrays == null) {
            return varrays = new ArrayList<RelatedResourceRep>();
        }
        return varrays;
    }

    public void setVirtualArrays(List<RelatedResourceRep> varrays) {
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

    @XmlElementWrapper(name = "assigned_storage_pools")
    /**
     * 
     * List of storage pools manually assigned to this virtual pool.
     * 
     * Previously assigned storage pools which are no longer matching
     * to this virtual pool, are not listed.
     * 
     */
    @XmlElement(name = "storage_pool")
    public List<RelatedResourceRep> getAssignedStoragePools() {
        if (assignedStoragePools == null) {
            assignedStoragePools = new ArrayList<RelatedResourceRep>();
        }
        return assignedStoragePools;
    }

    public void setAssignedStoragePools(List<RelatedResourceRep> assignedStoragePools) {
        this.assignedStoragePools = assignedStoragePools;
    }

    @XmlElementWrapper(name = "invalid_matched_pools")
    /**
     * List of storage pools that once were part of the matched pool but 
     * whose attributes no longer match.
     * 
     */
    @XmlElement(name = "storage_pool")
    public List<RelatedResourceRep> getInvalidMatchedStoragePools() {
        if (invalidMatchedStoragePools == null) {
            invalidMatchedStoragePools = new ArrayList<RelatedResourceRep>();
        }
        return invalidMatchedStoragePools;
    }

    public void setInvalidMatchedStoragePools(List<RelatedResourceRep> invalidMatchedStoragePools) {
        this.invalidMatchedStoragePools = invalidMatchedStoragePools;
    }

    @XmlElementWrapper(name = "matched_storage_pools")
    /**
     * Set of storage pools which has attributes that match the criteria for 
     * selecting the auto-generated list of storage pools.
     * 
     */
    @XmlElement(name = "storage_pool")
    public List<RelatedResourceRep> getMatchedStoragePools() {
        if (matchedStoragePools == null) {
            matchedStoragePools = new ArrayList<RelatedResourceRep>();
        }
        return matchedStoragePools;
    }

    public void setMatchedStoragePools(List<RelatedResourceRep> matchedStoragePools) {
        this.matchedStoragePools = matchedStoragePools;
    }
}

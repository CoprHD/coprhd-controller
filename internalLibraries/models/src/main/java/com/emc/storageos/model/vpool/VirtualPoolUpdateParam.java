/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

/**
 * Parameter to update VirtualPool
 */
@XmlRootElement(name = "vpool_update")
public class VirtualPoolUpdateParam {

    private VirtualArrayAssignmentChanges varrayChanges;
    private String name;
    private String description;
    private ProtocolChanges protocolChanges;
    private Boolean useMatchedPools;
    private String systemType;
    private String provisionType;

    public VirtualPoolUpdateParam() {
    }

    public VirtualPoolUpdateParam(VirtualArrayAssignmentChanges varrayChanges,
            String name, String description, ProtocolChanges protocolChanges,
            Boolean useMatchedPools, String systemType, String provisionType) {
        this.varrayChanges = varrayChanges;
        this.name = name;
        this.description = description;
        this.protocolChanges = protocolChanges;
        this.useMatchedPools = useMatchedPools;
        this.systemType = systemType;
        this.provisionType = provisionType;
    }

    /**
     * The virtual array assignment changes for the virtual pool.
     * 
     */
    @XmlElement(name = "varray_changes")
    public VirtualArrayAssignmentChanges getVarrayChanges() {
        return varrayChanges;
    }

    public void setVarrayChanges(VirtualArrayAssignmentChanges varrayChanges) {
        this.varrayChanges = varrayChanges;
    }

    /**
     * The new virtual pool name.
     * 
     */
    @XmlElement(name = "name")
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The new virtual pool description.
     * 
     */
    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * The protocol changes for the virtual pool.
     * 
     */
    @XmlElement(name = "protocol_changes")
    public ProtocolChanges getProtocolChanges() {
        return protocolChanges;
    }

    public void setProtocolChanges(ProtocolChanges protocolChanges) {
        this.protocolChanges = protocolChanges;
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
     * The new supported system type for the virtual pool.
     * Valid values:
     *  NONE
     *  vnxblock (Block)
     *  vmax (Block)
     *  vnxfile (File)
     *  isilon (File)
     *  netapp (File)
     * 
     */
    @XmlElement(name = "system_type")
    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    /**
     * The new provisioning type for the virtual pool,
     * Valid values:
     *  NONCE
     *  Thin
     *  Thick
     */
    @XmlElement(name = "provisioning_type", required = false)
    public String getProvisionType() {
        return provisionType;
    }

    public void setProvisionType(String provisionType) {
        this.provisionType = provisionType;
    }

}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
    
    public VirtualPoolUpdateParam() {}

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
     * @valid none
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
     * @valid none
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
     * @valid none
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
     * @valid none
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
     * @valid true
     * @valid false
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
     * 
     * @valid NONE
     * @valid vnxblock (Block)
     * @valid vmax     (Block)
     * @valid vnxfile  (File)
     * @valid isilon   (File)
     * @valid netapp   (File)
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
     * 
     * @valid NONE
     * @valid Thin
     * @valid Thick
     */
    @XmlElement(name = "provisioning_type", required = false)
    public String getProvisionType() {
        return provisionType;
    }

    public void setProvisionType(String provisionType) {
        this.provisionType = provisionType;
    }
    
    
}

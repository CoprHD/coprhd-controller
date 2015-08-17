/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.tier;

import java.util.LinkedHashSet;
import java.util.Set;
import javax.xml.bind.annotation.*;

import com.emc.storageos.model.DiscoveredDataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "auto_tiering_policy")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class AutoTieringPolicyRestRep extends DiscoveredDataObjectRestRep {
    private String storageGroupName;
    private RelatedResourceRep storageDevice;
    private Set<String> storagePools;
    private String policyName;
    private Boolean policyEnabled;
    private String provisioningType;
    private String systemType;

    /**
     * Determines the operational state of auto tiering policy
     * 
     * @valid true
     * @valid false
     * 
     */
    @XmlElement(name = "policy_enabled")
    public Boolean getPolicyEnabled() {
        return policyEnabled;
    }

    public void setPolicyEnabled(Boolean policyEnabled) {
        this.policyEnabled = policyEnabled;
    }

    /**
     * Name of the auto tiering policy
     * 
     * @valid none
     * 
     */
    @XmlElement(name = "policy_name")
    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    /**
     * The provisioning type of the auto tiering policy
     * 
     * @valid Thin
     * @valid Thick
     * @valid All
     */
    @XmlElement(name = "provisioning_type")
    public String getProvisioningType() {
        return provisioningType;
    }

    public void setProvisioningType(String provisioningType) {
        this.provisioningType = provisioningType;
    }

    /**
     * The type of storage system to which the auto tiering policy belongs
     * 
     * @valid vmax
     * @valid vnxblock
     */
    @XmlElement(name = "system_type")
    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    /**
     * The storage system on which this auto tiering policy resides.
     * 
     * @valid none
     */
    @XmlElement(name = "storage_system")
    public RelatedResourceRep getStorageDevice() {
        return storageDevice;
    }

    public void setStorageDevice(RelatedResourceRep storageDevice) {
        this.storageDevice = storageDevice;
    }

    /**
     * Name of the default storage group.
     * 
     * @valid none
     */
    @XmlElement(name = "storagegroup_name")
    public String getStorageGroupName() {
        return storageGroupName;
    }

    public void setStorageGroupName(String storageGroupName) {
        this.storageGroupName = storageGroupName;
    }

    /**
     * The list of storage pools associated with this auto tiering policy
     * 
     * @valid none
     * 
     */
    @XmlElement(name = "storage_pools")
    public Set<String> getStoragePools() {
        if (storagePools == null) {
            storagePools = new LinkedHashSet<String>();
        }
        return storagePools;
    }

    public void setStoragePools(Set<String> storagePools) {
        this.storagePools = storagePools;
    }
}

/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.tier;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DiscoveredDataObjectRestRep;

@XmlRootElement(name = "storage_tier")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class StorageTierRestRep extends DiscoveredDataObjectRestRep {
    private String enabledState;
    private String percentage;
    private Long totalCapacity;
    private String diskDriveTechnology;
    private Set<String> autoTierPolicy;

    /**
     * List of auto tiering policies, to which the storage tier is associated.
     * 
     * @valid none
     */
    @XmlElement(name = "auto_tier_policy")
    public Set<String> getAutoTieringPolicies() {
        if (autoTierPolicy == null) {
            autoTierPolicy = new LinkedHashSet<String>();
        }
        return autoTierPolicy;
    }

    public void setAutoTieringPolicies(Set<String> autoTierPolicy) {
        this.autoTierPolicy = autoTierPolicy;
    }

    /**
     * The underlying disk drive associated to this storage tier
     * 
     * @valid none
     */
    @XmlElement(name = "disk_drive_technology")
    public String getDiskDriveTechnology() {
        return diskDriveTechnology;
    }

    public void setDiskDriveTechnology(String diskDriveTechnology) {
        this.diskDriveTechnology = diskDriveTechnology;
    }

    /**
     * The operational state of the storage tier
     * 
     * @valid none
     */
    @XmlElement(name = "enabled_state")
    public String getEnabledState() {
        return enabledState;
    }

    public void setEnabledState(String enabledState) {
        this.enabledState = enabledState;
    }

    /**
     * The percentage of storage group space allocated for this storage tier
     * which can be used for auto tiering.
     * 
     * @valid none
     */
    @XmlElement(name = "percentage")
    public String getPercentage() {
        return percentage;
    }

    public void setPercentage(String percentage) {
        this.percentage = percentage;
    }

    /**
     * Total Capacity of the storage tier
     * 
     * @valid none
     */
    @XmlElement(name = "total_capacity")
    public Long getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(Long totalCapacity) {
        this.totalCapacity = totalCapacity;
    }
}

/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.pools;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.varray.VirtualArrayResourceUpdateParam;

import com.emc.storageos.model.valid.Range;

@XmlRootElement(name = "storage_pool_update")
public class StoragePoolUpdate extends VirtualArrayResourceUpdateParam {

    private Integer maxPoolUtilizationPercentage;
    private Integer maxThinPoolSubscriptionPercentage;
    private Integer maxResources;
    private Boolean isUnlimitedResourcesSet;

    public StoragePoolUpdate() {
    }

    public StoragePoolUpdate(VirtualArrayAssignmentChanges varrayChanges,
            Integer maxPoolUtilizationPercentage,
            Integer maxThinPoolSubscriptionPercentage, Integer maxResources) {
        super(varrayChanges);
        this.maxPoolUtilizationPercentage = maxPoolUtilizationPercentage;
        this.maxThinPoolSubscriptionPercentage = maxThinPoolSubscriptionPercentage;
        this.maxResources = maxResources;
    }

    /**
     * The user-defined limit for this pool's utilization
     * 
     * @valid none
     */
    @XmlElement(name = "max_pool_utilization_percentage")
    public Integer getMaxPoolUtilizationPercentage() {
        return maxPoolUtilizationPercentage;
    }

    public void setMaxPoolUtilizationPercentage(Integer maxPoolUtilizationPercentage) {
        this.maxPoolUtilizationPercentage = maxPoolUtilizationPercentage;
    }

    /**
     * The maximum percentage of usable space that can be assigned to thin volumes
     * 
     * @valid none
     */
    @XmlElement(name = "max_thin_pool_subscription_percentage")
    public Integer getMaxThinPoolSubscriptionPercentage() {
        return maxThinPoolSubscriptionPercentage;
    }

    public void setMaxThinPoolSubscriptionPercentage(
            Integer maxThinPoolSubscriptionPercentage) {
        this.maxThinPoolSubscriptionPercentage = maxThinPoolSubscriptionPercentage;
    }

    /**
     * The maximum number of ViPR storage resources that
     * can exist in this pool
     * 
     * @valid none
     */
    @XmlElement(name = "max_resources")
    @Range(min = 0, max = Integer.MAX_VALUE)
    public Integer getMaxResources() {
        return maxResources;
    }

    public void setMaxResources(Integer maxResources) {
        this.maxResources = maxResources;
    }

    /**
     * Whether limit on number of Resources has been set
     * 
     * @valid none
     */
    @XmlElement(name = "unlimited_resources")
    public Boolean getIsUnlimitedResourcesSet() {
        return isUnlimitedResourcesSet;
    }

    public void setIsUnlimitedResourcesSet(Boolean isUnlimitedResourcesSet) {
        this.isUnlimitedResourcesSet = isUnlimitedResourcesSet;
    }

}

/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.pools;

import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.StringHashMapEntry;
import com.emc.storageos.model.varray.VirtualArrayResourceRestRep;

import javax.xml.bind.annotation.*;

import java.util.*;

@XmlRootElement(name = "storage_pool")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class StoragePoolRestRep extends VirtualArrayResourceRestRep {
    private Set<String> protocols;
    private List<StringHashMapEntry> controllerParams;
    private String operationalStatus;
    private String registrationStatus;
    private Long totalCapacity;
    private Long freeCapacity;
    private Long usedCapacity;
    private Long percentUsed;
    private Long subscribedCapacity;
    private Long percentSubscribed;
    private Integer maxResources;
    private Integer numResources;
    private Long maximumThinVolumeSize;
    private Long minimumThinVolumeSize;
    private Long maximumThickVolumeSize;
    private Long minimumThickVolumeSize;
    private Set<String> raidLevels;
    private Set<String> driveTypes;
    private Set<String> copyTypes;
    private List<StringHashMapEntry> tierUtilizationPercentage;
    private String poolName;
    private String poolServiceType;
    private Boolean longTermRetention;
    private String supportedResourceTypes;
    private RelatedResourceRep storageSystem;
    private Integer maxThinPoolSubscriptionPercentage;
    private Integer maxPoolUtilizationPercentage;
    private Boolean thinVolumePreAllocationSupported;
    private Boolean autoTieringSupported;
    private String compatibilityStatus;
    private String discoveryStatus;
    private Integer dataCenters;

    public StoragePoolRestRep() {
    }

    @XmlElementWrapper(name = "controller_params")
    /**
     * The list of optional parameters
     * 
     */
    @XmlElement(name = "controller_param")
    public List<StringHashMapEntry> getControllerParams() {
        if (controllerParams == null) {
            controllerParams = new ArrayList<StringHashMapEntry>();
        }
        return controllerParams;
    }

    public void setControllerParams(List<StringHashMapEntry> controllerParams) {
        this.controllerParams = controllerParams;
    }

    /**
     * The supported local replication types for this pool
     * 
     */
    @XmlElementWrapper(name = "copy_types")
    /**
     * The type of replication. 
     * Valid values: 
     * ASYNC            = A copy can be maintained asynchronously
     * SYNC             = A copy can be maintained synchronously
     * UNSYNC_UNASSOC   = A full copy can be made, but there is no association between the source and target after making the copy
     * UNSYNC_ASSOC     = A full copy can be made, and there is an association between the source and target after making the copy
     *
     */
    @XmlElement(name = "copy_type")
    public Set<String> getCopyTypes() {
        if (copyTypes == null) {
            copyTypes = new LinkedHashSet<String>();
        }
        return copyTypes;
    }

    public void setCopyTypes(Set<String> copyTypes) {
        this.copyTypes = copyTypes;
    }

    /**
     * The disk drives types in the pool
     * 
     */
    @XmlElementWrapper(name = "drive_types")
    /**
     * The disk drive type
     * Valid values:
     *    FC   = Fibre-Channel
     *    SAS  = Serial Attached SCSI
     *    SATA = Serial ATA 
     */
    @XmlElement(name = "drive_type")
    public Set<String> getDriveTypes() {
        if (driveTypes == null) {
            driveTypes = new LinkedHashSet<String>();
        }
        return driveTypes;
    }

    public void setDriveTypes(Set<String> driveTypes) {
        this.driveTypes = driveTypes;
    }

    /**
     * The amount of free space in the pool (GB)
     * 
     */
    @XmlElement(name = "free_gb")
    public Long getFreeCapacity() {
        return freeCapacity;
    }

    public void setFreeCapacity(Long freeCapacity) {
        this.freeCapacity = freeCapacity;
    }

    /**
     * The maximum number of ViPR storage resources that
     * can exist in this pool
     * 
     */
    @XmlElement(name = "max_resources")
    public Integer getMaxResources() {
        return maxResources;
    }

    public void setMaxResources(Integer maxResources) {
        this.maxResources = maxResources;
    }

    /**
     * The number of ViPR storage resources that exist
     * in this pool
     * 
     */
    @XmlElement(name = "num_resources")
    public Integer getNumResources() {
        return numResources;
    }

    public void setNumResources(Integer numResources) {
        this.numResources = numResources;
    }

    /**
     * The operational status of the pool
     * Valid values:
     *   NOT_READY
     *   READY
     * 
     */
    @XmlElement(name = "operational_status")
    public String getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(String operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    /**
     * Whether or not this pool is registered with ViPR. A pool must be
     * registered before it can be managed by ViPR.
     * Valid values:
     *   REGISTERED
     *   UNREGISTERED
     */
    @XmlElement(name = "registration_status")
    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
    }

    /**
     * The percentage of usable space that has been assigned to all volumes
     * in the pool and visible to attached hosts. This includes primary
     * and snapshot space. (GB)
     * 
     */
    @XmlElement(name = "percent_subscribed")
    public Long getPercentSubscribed() {
        return percentSubscribed;
    }

    public void setPercentSubscribed(Long percentSubscribed) {
        this.percentSubscribed = percentSubscribed;
    }

    /**
     * The percentage of consumed usable space in the pool (GB)
     * 
     */
    @XmlElement(name = "percent_used")
    public Long getPercentUsed() {
        return percentUsed;
    }

    public void setPercentUsed(Long percentUsed) {
        this.percentUsed = percentUsed;
    }

    /**
     * The maximum size for thin volumes in this pool (GB)
     * 
     */
    @XmlElement(name = "maximum_thin_volume_size_gb")
    public Long getMaximumThinVolumeSize() {
        return maximumThinVolumeSize;
    }

    public void setMaximumThinVolumeSize(Long maximumThinVolumeSize) {
        this.maximumThinVolumeSize = maximumThinVolumeSize;
    }

    /**
     * The minimum size for thin volumes in this pool (GB)
     * 
     */
    @XmlElement(name = "minimum_thin_volume_size_gb")
    public Long getMinimumThinVolumeSize() {
        return minimumThinVolumeSize;
    }

    public void setMinimumThinVolumeSize(Long minimumThinVolumeSize) {
        this.minimumThinVolumeSize = minimumThinVolumeSize;
    }

    /**
     * The maximum size for thick volumes in this pool (GB)
     * 
     */
    @XmlElement(name = "maximum_thick_volume_size_gb")
    public Long getMaximumThickVolumeSize() {
        return maximumThickVolumeSize;
    }

    public void setMaximumThickVolumeSize(Long maximumThickVolumeSize) {
        this.maximumThickVolumeSize = maximumThickVolumeSize;
    }

    /**
     * The minimum size for thick volumes in this pool (GB)
     * 
     */
    @XmlElement(name = "minimum_thick_volume_size_gb")
    public Long getMinimumThickVolumeSize() {
        return minimumThickVolumeSize;
    }

    public void setMinimumThickVolumeSize(Long minimumThickVolumeSize) {
        this.minimumThickVolumeSize = minimumThickVolumeSize;
    }

    /**
     * The native name of the pool. This name is what the hosting storage
     * system uses to identify the pool.
     * 
     */
    @XmlElement(name = "pool_name")
    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = poolName;
    }

    /**
     * The ViPR storage resource type that can be provisioned in this pool
     * Valid values:
     *  block  = Volume
     *  file   = File System
     *  object = Object Store
     */
    @XmlElement(name = "pool_service_type")
    public String getPoolServiceType() {
        return poolServiceType;
    }

    public void setPoolServiceType(String poolServiceType) {
        this.poolServiceType = poolServiceType;
    }

    /**
     * The Long term retention policy is available on this pool
     * Valid values:
     *  block  = Volume
     *  file   = File System
     *  object = Object Store
     */
    @XmlElement(name = "long_term_retention")
    public Boolean getLongTermRetention() {
        return longTermRetention;
    }

    public void setLongTermRetention(boolean longTermRetention) {
        this.longTermRetention = longTermRetention;
    }

    /**
     * The protocols this pool uses to transport disk commands and
     * responses across its attached networks.
     * 
     */
    @XmlElementWrapper(name = "protocols")
    /**
     * Valid values:
     * 	FC = Fibre Channel
     * 	IP
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
     * The supported RAID levels for this pool
     * Valid values:
     *  RAID1
     *  RAID2
     *  RAID3
     *  RAID4
     *  RAID5
     *  RAID6
     *  RAID10 = RAID 1/0
     */
    @XmlElementWrapper(name = "raid_levels")
    /**
     */
    @XmlElement(name = "raid_level")
    public Set<String> getRaidLevels() {
        if (raidLevels == null) {
            raidLevels = new LinkedHashSet<String>();
        }
        return raidLevels;
    }

    public void setRaidLevels(Set<String> raidLevels) {
        this.raidLevels = raidLevels;
    }

    /**
     * The hosting storage system for this pool
     * 
     */
    @XmlElement(name = "storage_system")
    public RelatedResourceRep getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(RelatedResourceRep storageSystem) {
        this.storageSystem = storageSystem;
    }

    /**
     * The total amount of usable space that is configured in the pool
     * and is associated with resources provisioned in the pool. (GB)
     * 
     */
    @XmlElement(name = "subscribed_gb")
    public Long getSubscribedCapacity() {
        return subscribedCapacity;
    }

    public void setSubscribedCapacity(Long subscribedCapacity) {
        this.subscribedCapacity = subscribedCapacity;
    }

    /**
     * The volume types that can be created in this pool. A thin volume
     * allocates a portion of its assigned capacity when it is created
     * and grows as needed. A thick volume allocates all of its
     * assigned capacity when created.
     * Valid values:
     *  THICK_ONLY
     *  THIN_ONLY
     *  THIN_AND_THICK
     */
    @XmlElement(name = "supported_resource_types")
    public String getSupportedResourceTypes() {
        return supportedResourceTypes;
    }

    public void setSupportedResourceTypes(String supportedResourceTypes) {
        this.supportedResourceTypes = supportedResourceTypes;
    }

    @XmlElementWrapper(name = "tier_utilization_percentages")
    /**
     * The utilization percentages of each storage tier
     * 
     */
    @XmlElement(name = "tier_utilization_percentage")
    public List<StringHashMapEntry> getTierUtilizationPercentage() {
        if (tierUtilizationPercentage == null) {
            tierUtilizationPercentage = new ArrayList<StringHashMapEntry>();
        }
        return tierUtilizationPercentage;
    }

    public void setTierUtilizationPercentage(List<StringHashMapEntry> tierUtilizationPercentage) {
        this.tierUtilizationPercentage = tierUtilizationPercentage;
    }

    /**
     * The amount of usable space in the pool (GB)
     * 
     */
    @XmlElement(name = "usable_gb")
    public Long getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(Long totalCapacity) {
        this.totalCapacity = totalCapacity;
    }

    /**
     * The amount of consumed usable space in the pool (GB)
     * 
     */
    @XmlElement(name = "used_gb")
    public Long getUsedCapacity() {
        return usedCapacity;
    }

    public void setUsedCapacity(Long usedCapacity) {
        this.usedCapacity = usedCapacity;
    }

    /**
     * The user-defined limit for this pool's utilization
     * 
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
     */
    @XmlElement(name = "max_thin_pool_subscription_percentage")
    public Integer getMaxThinPoolSubscriptionPercentage() {
        return maxThinPoolSubscriptionPercentage;
    }

    public void setMaxThinPoolSubscriptionPercentage(Integer maxThinPoolSubscription) {
        this.maxThinPoolSubscriptionPercentage = maxThinPoolSubscription;
    }

    /**
     * Determines whether pre-allocation of thin volume is supported on this storage pool
     * 
     * 
     */
    @XmlElement(name = "thin_volume_preallocation_supported")
    public Boolean getThinVolumePreAllocationSupported() {
        return thinVolumePreAllocationSupported;
    }

    public void setThinVolumePreAllocationSupported(Boolean thinVolumePreAllocationSupported) {
        this.thinVolumePreAllocationSupported = thinVolumePreAllocationSupported;
    }

    /**
     * Determines whether auto-tiering is supported on this storage pool
     * 
     */
    @XmlElement(name = "auto_tiering_supported")
    public Boolean getAutoTieringSupported() {
        return autoTieringSupported;
    }

    /**
     * @param autoTieringSupported 
     *           the autoTieringEnabled to set
     */
    public void setAutoTieringSupported(Boolean autoTieringSupported) {
        this.autoTieringSupported = autoTieringSupported;
    }

    /**
     * Whether or not this storage pool is compatible with ViPR
     * Valid values
     *  COMPATIBLE
     *  INCOMPATIBLE
     *  UNKNOWN
     */
    @XmlElement(name = "compatibility_status")
    public String getCompatibilityStatus() {
        return compatibilityStatus;
    }

    public void setCompatibilityStatus(String compatibilityStatus) {
        this.compatibilityStatus = compatibilityStatus;
    }

    /**
     * Whether or not this storage pool is visible in discovery
     * Valid values:
     *  VISIBLE
     *  NOTINVISIBLE
     */
    @XmlElement(name = "discovery_status")
    public String getDiscoveryStatus() {
        return discoveryStatus;
    }

    public void setDiscoveryStatus(String discoveryStatus) {
        this.discoveryStatus = discoveryStatus;
    }
    
    /**
     * Number of data centers for storage pool
     * 
     * Valid value: Intergers
     */
    @XmlElement(name = "dataCenters")
    public Integer getDataCenters() {
        return dataCenters;
    }

    public void setDataCenters(Integer dataCenters) {
        this.dataCenters = dataCenters;
    }
    
}

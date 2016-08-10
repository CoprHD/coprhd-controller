/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonIgnore;

import com.emc.storageos.model.valid.Range;

/**
 * Parameters to create Block VirtualPool.
 */
@XmlRootElement(name = "block_vpool_create")
public class BlockVirtualPoolParam extends VirtualPoolCommonParam {

    private Integer maxPaths;
    private Integer minPaths;
    private Integer pathsPerInitiator;
    private Set<String> raidLevels;
    // For example, if auto_tiering_policy_name is specified ,then on vnx, ranking
    // algorithm is applied to get matching pools.
    // On vmax, only pools associated with vmax Auto Tier Policies are matched
    private String autoTieringPolicyName;
    private String driveType;
    private Integer thinVolumePreAllocationPercentage;
    private Boolean multiVolumeConsistency;
    private Boolean expandable;
    private Boolean fastExpansion; // used for VNMAX and VNX to use concatenated meta volumes vs. striped.
    private BlockVirtualPoolProtectionParam protection;
    private VirtualPoolHighAvailabilityParam highAvailability;
    private Boolean uniquePolicyNames;
    
    // compression setting for all flash vmax arrays
    private Boolean compressionEnabled;

    // VMAX Host IO Limits attributes
    private Integer hostIOLimitBandwidth; // Host Front End limit bandwidth. If not specified or 0, indicated unlimited
    private Integer hostIOLimitIOPs; // Host Front End limit I/O. If not specified or 0, indicated unlimited
    
    // De-duplication supported vpool
    private Boolean dedupCapable;

    // resource placement policy
    private String placementPolicy;

    public BlockVirtualPoolParam() {
    }

    /**
     * The maximum number of paths to a given StorageArray from a host.
     * Depending on paths_per_initiator, one or more ports may be assigned to
     * an initiator if max_paths is sufficiently high for the number of initiators.
     * <p>
     * The number of paths is balanced across multiple networks (as determined from the initiators) if possible.
     * <p>
     * This variable repalces num_paths (which is deprecated) but essentially did the same thing. The new name emphasizes that this is the
     * maximum number of paths that will be provisioned. Port usage will not be more than the lessor of number of initiators *
     * paths_per_initiator or max_paths, whichever is smaller.
     * <p>
     * The Storage Pool matcher will not match pools where the array containing the pool has less usable ports than max_paths.
     * 
     */
    @XmlElement(name = "max_paths")
    @Range(min = 1, max = 65535)
    public Integer getMaxPaths() {
        return maxPaths;
    }

    public void setMaxPaths(Integer maxPaths) {
        this.maxPaths = maxPaths;
    }

    /**
     * The minimum number of paths that can be used between a host and a storage volume.
     * If this many paths cannot be configured, Export requests will fail.
     * 
     */
    @XmlElement(name = "min_paths")
    @Range(min = 1, max = 65535)
    public Integer getMinPaths() {
        return minPaths;
    }

    public void setMinPaths(Integer minPaths) {
        this.minPaths = minPaths;
    }

    /**
     * @deprecated use getMaxPaths instead of getNumPaths
     *      TODO: Remove deprecated API calls in next major release
     */
    @Deprecated
    @XmlElement(name = "num_paths")
    @Range(min = 1, max = 65535)
    public Integer getNumPaths() {
        return maxPaths;
    }

    /**
     * @deprecated use setMaxPaths instead of setNumPaths
     *      TODO: Remove deprecated API calls in next major release
     */
    @Deprecated
    public void setNumPaths(Integer numPaths) {
        this.maxPaths = numPaths;
    }

    /**
     * The number of paths to be provisioned for each initiator that is used.
     * In any event no more ports are used per host than max_paths.
     * If there are excess initiators that cannot be paired with paths_per_initiator
     * number of ports because max_paths is too low,
     * the excess initiators are not provisioned.
     * 
     */
    @XmlElement(name = "paths_per_initiator")
    @Range(min = 1, max = 65535)
    public Integer getPathsPerInitiator() {
        return pathsPerInitiator;
    }

    public void setPathsPerInitiator(Integer pathsPerInitiator) {
        this.pathsPerInitiator = pathsPerInitiator;
    }

    @XmlElementWrapper(name = "raid_levels")
    /**
     * Raid Levels can be specified, only if System Type is specified.
     * Raid Levels are supported only for System Types: vmax, vnxblock
     * For example, if RAID5 and RAID6 are specified, only pools that 
     * support RAID5 and RAID6 are matched.
     * 
     * RAID levels set the amount of redundancy and striping.
     * Here is a quick definition of the various RAID levels.
     * 
     * RAID 0 is a striped set of disks without parity.
     * RAID 1 is a mirror copy on two disks.
     * RAID 2 is a stripe at the bit level rather than the block level. Rarely used or supported.
     * RAID 3 is a byte level striping with a dedicated parity disk.
     * RAID 4 is block level striping with a dedicated parity disk.
     * RAID 5 is block level striping with the parity data distributed across all disks.
     * RAID 6 extends RAID 5 by adding an additional parity block; 
     * thus it uses block level striping with two parity blocks.
     * RAID 10 is a stripe of mirrors, i.e. a RAID 0 combination of RAID 1 drives.
     * Valid values:
     *  RAID0
     *  RAID1
     *  RAID2
     *  RAID3
     *  RAID4
     *  RAID5
     *  RAID6
     *  RAID10
     */
    @XmlElement(name = "raid_level")
    public Set<String> getRaidLevels() {
        // TODO: empty collection workaround
        // if (raidLevels == null) {
        // raidLevels = new LinkedHashSet<String>();
        // }
        return raidLevels;
    }

    public void setRaidLevels(Set<String> raidLevels) {
        this.raidLevels = raidLevels;
    }

    /**
     * AutoTiering Policy Name can be specified, only if System Type is specified.
     * AutoTiering Policy Name is supported only for System Types: vmax, vnxblock
     * 
     */
    @XmlElement(name = "auto_tiering_policy_name")
    public String getAutoTieringPolicyName() {
        return autoTieringPolicyName;
    }

    public void setAutoTieringPolicyName(String autoTieringPolicyName) {
        this.autoTieringPolicyName = autoTieringPolicyName;
    }

    /**
     * Supported Drive Type. 
     * Valid values:
     *	NONE = No specific drive type
     *  SSD = Solid State Drive
     *  FC = Fibre Channel
     *  SAS = Serial Attached SCSI
     *  SATA = Serial Advanced Technology Attachment
     * 
     */
    @XmlElement(name = "drive_type")
    public String getDriveType() {
        return driveType;
    }

    public void setDriveType(String driveType) {
        this.driveType = driveType;
    }

    /**
     * PreAllocation size for VMAX Thin volumes.
     * 
     */
    @XmlElement(name = "thin_volume_preallocation_percentage")
    public Integer getThinVolumePreAllocationPercentage() {
        return thinVolumePreAllocationPercentage;
    }

    public void setThinVolumePreAllocationPercentage(
            Integer thinVolumePreAllocationPercentage) {
        this.thinVolumePreAllocationPercentage = thinVolumePreAllocationPercentage;
    }

    /**
     * Flag to specify whether a volume created in this pool could
     * be added to a Consistency Group.
     * 
     */
    @XmlElement(name = "multi_volume_consistency")
    public Boolean getMultiVolumeConsistency() {
        return multiVolumeConsistency;
    }

    public void setMultiVolumeConsistency(Boolean multiVolumeConsistency) {
        this.multiVolumeConsistency = multiVolumeConsistency;
    }

    /**
     * Indicates that virtual pool volumes should use concatenated meta volumes,
     * not striped.
     * 
     */
    @XmlElement(name = "fast_expansion", required = false)
    public Boolean getFastExpansion() {
        return fastExpansion;
    }

    public void setFastExpansion(Boolean fastExpansion) {
        this.fastExpansion = fastExpansion;
    }

    /**
     * Indicates if volume expansion is supported.
     * 
     */
    @XmlElement(name = "expandable", required = false)
    public Boolean getExpandable() {
        return expandable;
    }

    public void setExpandable(Boolean expandable) {
        this.expandable = expandable;
    }

    /**
     * Virtual Pool (Mirror or RecoverPoint) protection
     * parameters.
     *
     */
    @XmlElement(name = "protection")
    public BlockVirtualPoolProtectionParam getProtection() {
        return protection;
    }

    public void setProtection(BlockVirtualPoolProtectionParam protection) {
       this.protection = protection;
    }

    /**
     * Convenience method for checking for protection
     * 
     * @return true if protection exists
     */
    public boolean hasRemoteCopyProtection() {
        if ((getProtection() != null) &&
                (getProtection().getRemoteCopies() != null) &&
                (getProtection().getRemoteCopies().getRemoteCopySettings() != null)) {
            return true;
        }
        return false;
    }

    /**
     * High availability type for the Virtual Pool.
     * 
     */
    @XmlElement(name = "high_availability")
    public VirtualPoolHighAvailabilityParam getHighAvailability() {
        return highAvailability;
    }

    public void setHighAvailability(
            VirtualPoolHighAvailabilityParam highAvailability) {
        this.highAvailability = highAvailability;
    }

    /**
     * If set to true, then only unique Auto Tiering Policy Names
     * will be returned else all policies will be returned.
     * 
     */
    @XmlElement(name = "unique_auto_tier_policy_names", required = false)
    public Boolean getUniquePolicyNames() {
        return uniquePolicyNames;
    }

    public void setUniquePolicyNames(Boolean uniquePolicyNames) {
        this.uniquePolicyNames = uniquePolicyNames;
    }

    /**
     * Convenience method that determines if high availability
     * has been specified.
     * 
     * @return
     */
    public boolean specifiesHighAvailability() {
        return (highAvailability != null && ((HighAvailabilityType.vplex_local
                .name().equals(highAvailability.getType())) || (HighAvailabilityType.vplex_distributed
                .name().equals(highAvailability.getType()))));
    }

    @XmlElement(name = "host_io_limit_bandwidth", required = false)
    public Integer getHostIOLimitBandwidth() {
        return hostIOLimitBandwidth;
    }

    public void setHostIOLimitBandwidth(Integer hostIOLimitBandwidth) {
        this.hostIOLimitBandwidth = hostIOLimitBandwidth;
    }

    @XmlElement(name = "host_io_limit_iops", required = false)
    public Integer getHostIOLimitIOPs() {
        return hostIOLimitIOPs;
    }

    public void setHostIOLimitIOPs(Integer hostIOLimitIOPs) {
        this.hostIOLimitIOPs = hostIOLimitIOPs;
    }

    /**
     * @return the compressionEnabled
     */
    @XmlElement(name = "compression_enabled", required = false)
    public Boolean getCompressionEnabled() {
        return compressionEnabled;
    }

    /**
     * @param compressionEnabled the compressionEnabled to set
     */
    public void setCompressionEnabled(Boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
    }

    @JsonIgnore
    public boolean isHostIOLimitBandwidthSet() {
        return hostIOLimitBandwidth != null && hostIOLimitBandwidth > 0;
    }

    @JsonIgnore
    public boolean isHostIOLimitIOPsSet() {
        return hostIOLimitIOPs != null && hostIOLimitIOPs > 0;
    }

    @XmlElement(name = "dedup_capable", required = false)
	public Boolean getDedupCapable() {
		return dedupCapable;
	}

	public void setDedupCapable(Boolean dedupCapable) {
		this.dedupCapable = dedupCapable;
	}

    /**
     * Resource placement policy used by the virtual pool.
     * Valid values:
     *  default_policy (storage system/pool selection based on metrics and capacity)
     *  array_affinity (storage system/pool selection based on host/cluster's array affinity first, then metrics and capacity)
     */
    @XmlElement(name = "placement_policy")
    public String getPlacementPolicy() {
        return placementPolicy;
    }

    public void setPlacementPolicy(String placementPolicy) {
        this.placementPolicy = placementPolicy;
    }

}

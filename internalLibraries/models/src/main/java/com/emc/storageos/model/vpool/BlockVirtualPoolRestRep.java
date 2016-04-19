/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.vpool;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "block_vpool")
public class BlockVirtualPoolRestRep extends VirtualPoolCommonRestRep {
    private String autoTieringPolicyName;
    private String driveType;
    private Boolean expandable;
    public Boolean fastExpansion;
    private Boolean multiVolumeConsistent;
    private Integer thinVolumePreAllocationPercentage;
    private BlockVirtualPoolProtectionParam protection;
    private VirtualPoolHighAvailabilityParam highAvailability;
    private Set<String> raidLevels;
    private Boolean uniquePolicyNames;
    private Integer maxPaths;
    private Integer minPaths;
    private Integer pathsPerInitiator;

    // VMAX Host IO Limits attributes
    private Integer hostIOLimitBandwidth; // Host Front End limit bandwidth. If not specified or 0, indicated unlimited
    private Integer hostIOLimitIOPs; // Host Front End limit I/O. If not specified or 0, indicated unlimited

    public BlockVirtualPoolRestRep() {
    }

    /**
     * Name of the auto tier policy for the virtual pool.
     *
     * 
     * @return The auto tier policy name.
     */
    @XmlElement(name = "auto_tiering_policy_name")
    public String getAutoTieringPolicyName() {
        return autoTieringPolicyName;
    }

    /**
     * The supported disk drive type for the virtual pool. 
     * Valid values:
     *  NONE = No specific drive type
     *  SSD = Solid State Drive
     *  FC = Fibre Channel
     *  SAS = Serial Attached SCSI
     *  SATA = Serial Advanced Technology Attachment
     *  
     * @return The drive type.
     */
    @XmlElement(name = "drive_type")
    public String getDriveType() {
        return driveType;
    }

    /**
     * Specifies whether or not volumes can be expanded.
     * 
     * @return true if volumes are expandable, false otherwise.
     */
    @XmlElement(name = "expandable")
    public Boolean getExpandable() {
        return expandable;
    }

    /**
     * Indicates that virtual pool volumes should use concatenated meta volumes,
     * not striped.
     * 
     */
    @XmlElement(name = "fast_expansion")
    public Boolean getFastExpansion() {
        return fastExpansion;
    }

    public void setFastExpansion(Boolean fastExpansion) {
        this.fastExpansion = fastExpansion;
    }

    /**
     * The high availability settings for the virtual pool.
     * 
     * 
     * @return The high availability settings for the virtual pool.
     */
    @XmlElement(name = "high_availability")
    public VirtualPoolHighAvailabilityParam getHighAvailability() {
        return highAvailability;
    }

    /**
     * The preallocation size for VMAX thin volumes.
     * 
     * 
     * @return The preallocation size for VMAX thin volumes.
     */
    @XmlElement(name = "thin_volume_preallocation_percentage")
    public Integer getThinVolumePreAllocationPercentage() {
        return thinVolumePreAllocationPercentage;
    }

    /**
     * Specifies whether or not multi-volume consistency is supported for the
     * virtual pool.
     * 
     * 
     * @return true if multi-volume consistency is supported, false otherwise.
     */
    @XmlElement(name = "multi_volume_consistency")
    public Boolean getMultiVolumeConsistent() {
        return multiVolumeConsistent;
    }

    /**
     * The protection settings for the virtual pool.
     * 
     * 
     * @return The protection settings for the virtual pool.
     */
    @XmlElement
    public BlockVirtualPoolProtectionParam getProtection() {
        return protection;
    }

    @XmlElementWrapper(name = "raid_levels")
    /**
     * The RAID levels for storage allocated to your volumes.
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
     * 
     * @return the supported RAID levels.
     */
    @XmlElement(name = "raid_level")
    public Set<String> getRaidLevels() {
        if (raidLevels == null) {
            raidLevels = new LinkedHashSet<String>();
        }
        return raidLevels;
    }

    public void setAutoTieringPolicyName(String autoTieringPolicyName) {
        this.autoTieringPolicyName = autoTieringPolicyName;
    }

    public void setDriveType(String driveType) {
        this.driveType = driveType;
    }

    public void setExpandable(Boolean expandable) {
        this.expandable = expandable;
    }

    public void setHighAvailability(VirtualPoolHighAvailabilityParam highAvailability) {
        this.highAvailability = highAvailability;
    }

    public void setThinVolumePreAllocationPercentage(Integer thinVolumePreAllocationPercentage) {
        this.thinVolumePreAllocationPercentage = thinVolumePreAllocationPercentage;
    }

    public void setMultiVolumeConsistent(Boolean multiVolumeConsistent) {
        this.multiVolumeConsistent = multiVolumeConsistent;
    }

    public void setProtection(BlockVirtualPoolProtectionParam protection) {
        this.protection = protection;
    }

    public void setRaidLevels(Set<String> raidLevels) {
        this.raidLevels = raidLevels;
    }

    /**
     * Specifies whether or not unique auto tier policy names are required.
     * 
     * 
     * @return true if unique auto tier policy names are required.
     */
    @XmlElement(name = "unique_auto_tier_policy_names")
    public Boolean getUniquePolicyNames() {
        return uniquePolicyNames;
    }

    public void setUniquePolicyNames(Boolean uniquePolicyNames) {
        this.uniquePolicyNames = uniquePolicyNames;
    }

    /**
     * Number of max paths supported by this virtual pool.
     * 
     */
    @XmlElement(name = "max_paths")
    public Integer getMaxPaths() {
        return maxPaths;
    }

    public void setMaxPaths(Integer maxPaths) {
        this.maxPaths = maxPaths;
    }

    /*
     * Minimum number of paths to be exported by this virtual pool.
     * 
     */
    @XmlElement(name = "min_paths")
    public Integer getMinPaths() {
        return minPaths;
    }

    public void setMinPaths(Integer minPaths) {
        this.minPaths = minPaths;
    }

    /**
     * Number of paths to be provisioned per initiator.
     * 
     */
    @XmlElement(name = "paths_per_initiator")
    public Integer getPathsPerInitiator() {
        return pathsPerInitiator;
    }

    public void setPathsPerInitiator(Integer pathsPerInitiator) {
        this.pathsPerInitiator = pathsPerInitiator;
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
}

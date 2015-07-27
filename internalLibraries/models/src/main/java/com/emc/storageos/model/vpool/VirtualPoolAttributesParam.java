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

import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.HashSet;
import java.util.Set;

@XmlRootElement(name = "find_matching_pools")
public class VirtualPoolAttributesParam {

    private Set<String> protocols;
    private Integer maxPaths;
    private Integer minPaths;
    private Integer pathsPerInitiator;
    private Set<String> virtualArrays;
    private VirtualPoolProtectionParam protection;
    private String provisionType;
    private VirtualPoolHighAvailabilityParam highAvailability;
    private String systemType;
    private Set<String> raidLevels;
    private String autoTieringPolicyName;
    private String driveType;
    private Boolean multiVolumeConsistency;
    
    public VirtualPoolAttributesParam() {}

    @XmlElementWrapper(name = "protocols")
    /**
     * The protocols for a virtual pool.
     * 
     * @valid FC = Fibre Channel (block)
     * @valid ISCSI =  Internet Small Computer System Interface (block)
     * @valid FCoE = Fibre Channel over Ethernet (block)
     * @valid NFS = Network File System (file)
     * @valid NFSv4 = Network File System Version 4 (file)
     * @valid CIFS = Common Internet File System (file)
     */
    @XmlElement(name = "protocol")
    public Set<String> getProtocols() {
        return protocols;
    }

    public void setProtocols(Set<String> protocols) {
        if (protocols == null) {
            protocols = new HashSet<String>();
        }
        this.protocols = protocols;
    }

    /**
     * The maximum number of paths to a given storage system.
     * 
     * @valid none
     */
    @XmlElement(name = "max_paths")
    public Integer getMaxPaths() {
        return maxPaths;
    }

    public void setMaxPaths(Integer maxPaths) {
        this.maxPaths = maxPaths;
    }
    
    /**
     * The mininm number of paths to a given storage system for export.
     * 
     * @valid none
     */
    @XmlElement(name = "min_paths")
    public Integer getMinPaths() {
        return minPaths;
    }
    
    public void setMinPaths(Integer minPaths) {
        this.minPaths = minPaths;
    }

    /**
     * @deprecated use getMaxPaths instead of getNumPaths
     */
    @Deprecated
    @XmlElement(name = "num_paths")
    public Integer getNumPaths() {
        return maxPaths;
    }

    /**
     * @deprecated use setMaxPaths instead of setNumPaths
     */
    @Deprecated
    public void setNumPaths(Integer numPaths) {
        this.maxPaths = numPaths;
    }

    /**
     * The maximum number of paths to a given storage system for the initiator.
     */
    @XmlElement(name = "paths_per_initiator")
    public Integer getPathsPerInitiator() {
        return pathsPerInitiator;
    }

    public void setPathsPerInitiator(Integer pathsPerInitiator) {
        this.pathsPerInitiator = pathsPerInitiator;
    }

    @XmlElementWrapper(name = "varrays")
    /**
     * The virtual arrays for the virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "varray")
    @JsonProperty("varrays")
    public Set<String> getVirtualArrays() {
        if (virtualArrays == null) {
            virtualArrays = new HashSet<String>();
        }
        return virtualArrays;
    }

    public void setVirtualArrays(Set<String> virtualArrays) {
        this.virtualArrays = virtualArrays;
    }

    /**
     * The protection settings for the virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "protection")
    public VirtualPoolProtectionParam getProtection() {
        return protection;
    }

    public void setProtection(VirtualPoolProtectionParam protection) {
        this.protection = protection;
    }

    /**
     * The provisioning type for the virtual pool.
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

    /**
     * The high availability settings for the virtual pool.
     * 
     * @valid none
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
     * The system type for the virtual pool.
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

    @XmlElementWrapper(name = "raid_levels")
    /**
     * The desired RAID levels. Only supported for VMAX and VNX storage systems.
     * When specified, only storage pools that support the specified RAID levels
     * are matched. During volume creation, a specific RAID level to be used may
     * be specified. This RAID level must be supported by the virtual pool.
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
     * 
     * @valid RAID0
     * @valid RAID1
     * @valid RAID2
     * @valid RAID3
     * @valid RAID4
     * @valid RAID5
     * @valid RAID6
     * @valid RAID10
     */ 
    @XmlElement(name = "raid_level")
    public Set<String> getRaidLevels() {
        if (raidLevels == null) {
            raidLevels = new HashSet<String>();
        }
        return raidLevels;
    }

    public void setRaidLevels(Set<String> raidLevels) {
        this.raidLevels = raidLevels;
    }

    /**
     * The auto tier policy name. Only supported for VMAX and VNX storage
     * systems. If auto_tiering_policy_name is specified, then on VNX, a ranking
     * algorithm is applied to get matching pools. On VMAX, only pools
     * associated with VMAX Auto Tier Policies are matched.
     * 
     * @valid none
     */
    @XmlElement(name="auto_tiering_policy_name")
    public String getAutoTieringPolicyName() {
        return autoTieringPolicyName;
    }

    public void setAutoTieringPolicyName(String autoTieringPolicyName) {
        this.autoTieringPolicyName = autoTieringPolicyName;
    }

    /**
     * The supported drive type. When specified, only storage pools that are
     * comprised of the specified drive type are matched.
     * 
     * @valid NONE = No specific drive type
     * @valid SSD = Solid State Drive
     * @valid FC = Fibre Channel
     * @valid SAS = Serial Attached SCSI
     * @valid SATA = Serial Advanced Technology Attachment 
     */
    @XmlElement(name="drive_type")
    public String getDriveType() {
        return driveType;
    }

    public void setDriveType(String driveType) {
        this.driveType = driveType;
    }

    /**
     * Specifies whether or not the virtual pool supports multi-volume
     * consistency. When specified for a virtual pool, volumes created using the
     * virtual pool can be created in consistency groups.
     * 
     * @valid true
     * @valid false
     */
    @XmlElement(name = "multi_volume_consistency")
    public Boolean getMultiVolumeConsistency() {
        return multiVolumeConsistency;
    }

    public void setMultiVolumeConsistency(Boolean multiVolumeConsistency) {
        this.multiVolumeConsistency = multiVolumeConsistency;
    }
    
}

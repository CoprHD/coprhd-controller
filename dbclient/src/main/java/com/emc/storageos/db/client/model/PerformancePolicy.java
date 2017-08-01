/*
 * Copyright 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.util.NullColumnValueGetter;

/**
 * This column family captures array performance parameters that are deprecated from
 * the VirtualPool column family as part of the virtual pool simplification project.
 * Like VirtualPools, PerformancePolicy instances can be specified for volumes when they
 * are provisioned.
 */
@Cf("PerformancePolicy")
@SuppressWarnings("serial")
public class PerformancePolicy extends DataObjectWithACLs implements GeoVisibleResource {
    
    // Default values
    public static final String PP_DFLT_AUTOTIERING_POLICY_NAME = "NONE";
    public static final Boolean PP_DFLT_COMPRESSION_ENABLED = Boolean.TRUE;
    public static final Integer PP_DFLT_HOST_IO_LIMIT_BANDWIDTH = 0;
    public static final Integer PP_DFLT_HOST_IO_LIMIT_IOPS = 0;
    public static final Integer PP_DFLT_THIN_VOLUME_PRE_ALLOC_PERCENTAGE = 10;
    public static final Boolean PP_DFLT_DEDUP_CAPABLE = Boolean.TRUE;
    public static final Boolean PP_DFLT_FAST_EXPANSION = Boolean.TRUE;

    // A user supplied description.
    private String description;

    // The FAST Policy Name.
    private String autoTierPolicyName;

    // Compression setting for all Flash VMAX3 storage arrays.
    private Boolean compressionEnabled;

    // VMAX Host I/O bandwidth limit. Unlimited when not specified or 0.
    private Integer hostIOLimitBandwidth;

    // VMAX Host I/O IOPS limit. Unlimited when not specified or 0.
    private Integer hostIOLimitIOPs;

    // Percentage of the requested volume size that is allocated when provisioning thin volumes.
    private Integer thinVolumePreAllocationPercentage;

    // Whether or not volumes should be provisioned in storage pools that support deduplication.
    private Boolean dedupCapable;

    // Whether or not to use striped (false) or concatenated (true) meta-volumes when expanding volumes.
    private Boolean fastExpansion = false;

    /*
     * Required member variable getters and setters.
     */

    @Name("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
        setChanged("description");
    }

    @Name("autoTierPolicyName")
    public String getAutoTierPolicyName() {
        return autoTierPolicyName;
    }

    public void setAutoTierPolicyName(final String autoTierPolicyName) {
        if (NullColumnValueGetter.isNullValue(autoTierPolicyName)) {
            this.autoTierPolicyName = PerformancePolicy.PP_DFLT_AUTOTIERING_POLICY_NAME;
        } else {
            this.autoTierPolicyName = autoTierPolicyName;
        }
        setChanged("autoTierPolicyName");
    }

    @Name("compressionEnabled")
    public Boolean getCompressionEnabled() {
        return compressionEnabled == null ? PerformancePolicy.PP_DFLT_COMPRESSION_ENABLED : compressionEnabled;
    }

    public void setCompressionEnabled(final Boolean compressionEnabled) {
        if (compressionEnabled == null) {
            this.compressionEnabled = PerformancePolicy.PP_DFLT_COMPRESSION_ENABLED;
        } else {
            this.compressionEnabled = compressionEnabled;
        }
        setChanged("compressionEnabled");
    }

    @Name("hostIOLimitBandwidth")
    public Integer getHostIOLimitBandwidth() {
        return hostIOLimitBandwidth;
    }

    public void setHostIOLimitBandwidth(final Integer hostIOLimitBandwidth) {
        if (hostIOLimitBandwidth == null) {
            this.hostIOLimitBandwidth = PerformancePolicy.PP_DFLT_HOST_IO_LIMIT_BANDWIDTH;
        } else {
            this.hostIOLimitBandwidth = hostIOLimitBandwidth;
        }
        setChanged("hostIOLimitBandwidth");
    }

    @Name("hostIOLimitIOPs")
    public Integer getHostIOLimitIOPs() {
        return hostIOLimitIOPs;
    }

    public void setHostIOLimitIOPs(final Integer hostIOLimitIOPs) {
        if (hostIOLimitIOPs == null) {
            this.hostIOLimitIOPs = PerformancePolicy.PP_DFLT_HOST_IO_LIMIT_IOPS;
        } else {
            this.hostIOLimitIOPs = hostIOLimitIOPs;
        }
        setChanged("hostIOLimitIOPs");
    }

    @Name("thinVolumePreAllocationPercentage")
    public Integer getThinVolumePreAllocationPercentage() {
        return thinVolumePreAllocationPercentage;
    }

    public void setThinVolumePreAllocationPercentage(final Integer thinVolumePreAllocationPercentage) {
        if (thinVolumePreAllocationPercentage == null) {
            this.thinVolumePreAllocationPercentage = PerformancePolicy.PP_DFLT_THIN_VOLUME_PRE_ALLOC_PERCENTAGE;
        } else {
            this.thinVolumePreAllocationPercentage = thinVolumePreAllocationPercentage;
        }
        setChanged("thinVolumePreAllocationPercentage");
    }

    @Name("dedupCapable")
    public Boolean getDedupCapable() {
        return dedupCapable;
    }

    public void setDedupCapable(final Boolean dedupCapable) {
        if (dedupCapable == null) {
            this.dedupCapable = PerformancePolicy.PP_DFLT_DEDUP_CAPABLE;
        } else {
            this.dedupCapable = dedupCapable;
        }
        setChanged("dedupCapable");
    }

    @Name("fastExpansion")
    public Boolean getFastExpansion() {
        return fastExpansion;
    }

    public void setFastExpansion(final Boolean fastExpansion) {
        if (fastExpansion == null) {
            this.fastExpansion = PerformancePolicy.PP_DFLT_FAST_EXPANSION;
        } else {
            this.fastExpansion = fastExpansion;
        }
        setChanged("fastExpansion");
    }

    /*
     * Utility Methods
     */

    /**
     * Returns whether or not a Host I/O IOPS limit is set.
     * 
     * @return true if a limit is set, false otherwise.
     */
    public boolean checkHostIOLimitIOPsSet() {
        return hostIOLimitIOPs != null && hostIOLimitIOPs > 0;
    }

    /**
     * Returns whether or not a Host I/O bandwidth limit is set.
     * 
     * @return true if a limit is set, false otherwise.
     */
    public boolean checkHostIOLimitBandwidthSet() {
        return hostIOLimitBandwidth != null && hostIOLimitBandwidth > 0;
    }
}

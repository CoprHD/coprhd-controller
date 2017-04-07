/*
 * Copyright 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 * This column family captures array performance parameters that are deprecated from
 * the VirtualPool column family as part of the virtual pool simplification project.
 * Like VirtualPools, PerformanceParam instances can be specified for volumes when they
 * are provisioned.
 */
@Cf("PerformanceParams")
@SuppressWarnings("serial")
public class PerformanceParams extends DataObjectWithACLs implements GeoVisibleResource {

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
        this.autoTierPolicyName = autoTierPolicyName;
        setChanged("autoTierPolicyName");
    }

    @Name("compressionEnabled")
    public Boolean getCompressionEnabled() {
        return compressionEnabled == null ? false : compressionEnabled;
    }

    public void setCompressionEnabled(final Boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
        setChanged("compressionEnabled");
    }

    @Name("hostIOLimitBandwidth")
    public Integer getHostIOLimitBandwidth() {
        return hostIOLimitBandwidth;
    }

    public void setHostIOLimitBandwidth(final Integer limitHostBandwidth) {
        this.hostIOLimitBandwidth = limitHostBandwidth == null ? null : Math.max(0, limitHostBandwidth);
        setChanged("hostIOLimitBandwidth");
    }

    @Name("hostIOLimitIOPs")
    public Integer getHostIOLimitIOPs() {
        return hostIOLimitIOPs;
    }

    public void setHostIOLimitIOPs(final Integer limitHostIOPs) {
        this.hostIOLimitIOPs = limitHostIOPs == null ? null : Math.max(0, limitHostIOPs);
        setChanged("hostIOLimitIOPs");
    }

    @Name("thinVolumePreAllocationPercentage")
    public Integer getThinVolumePreAllocationPercentage() {
        return thinVolumePreAllocationPercentage;
    }

    public void setThinVolumePreAllocationPercentage(final Integer thinVolumePreAllocationPercentage) {
        this.thinVolumePreAllocationPercentage = thinVolumePreAllocationPercentage;
        setChanged("thinVolumePreAllocationPercentage");
    }

    @Name("dedupCapable")
    public Boolean getDedupCapable() {
        if (null == dedupCapable) {
            return false;
        }
        return dedupCapable;
    }

    public void setDedupCapable(final Boolean dedupCapable) {
        if (null == dedupCapable) {
            this.dedupCapable = false;
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
        this.fastExpansion = fastExpansion;
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

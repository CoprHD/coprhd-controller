/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

public class StorageGroupType {

    // min/max occurs: 1/1
    private String storageGroupId;
	// min/max occurs: 0/1
    private String slo;
	// min/max occurs: 0/1
    private String base_slo_name;
	// min/max occurs: 0/1
    private String srp;
	// min/max occurs: 0/1
    private String workload;
	// min/max occurs: 0/1
    private String slo_compliance;
	// min/max occurs: 0/1
    private Integer num_of_vols;
	// min/max occurs: 0/1
    private Long num_of_child_sgs;
	// min/max occurs: 0/1
    private Long num_of_parent_sgs;
	// min/max occurs: 0/1
    private Long num_of_masking_views;
	// min/max occurs: 0/1
    private Long num_of_snapshots;
	// min/max occurs: 0/1
    private Double cap_gb;
	// min/max occurs: 0/1
    private String device_emulation;
	// min/max occurs: 0/1
    private String type;
	// min/max occurs: 0/1
    private Boolean unprotected;
	// min/max occurs: 0/unbounded
    private String[] child_storage_group;
	// min/max occurs: 0/unbounded
    private String[] parent_storage_group;
	// min/max occurs: 0/unbounded
    private String[] maskingview;
	// min/max occurs: 0/1
    private HostIOLimitType hostIOLimit;
	// min/max occurs: 0/1
    private Boolean compression;
	// min/max occurs: 0/1
    private String compressionRatio;
	// min/max occurs: 0/1
    private Double compression_ratio_to_one;
	// min/max occurs: 0/1
    private String VPSaved;

    public String getStorageGroupId() {
        return storageGroupId;
    }

    public String getSlo() {
        return slo;
    }

    public String getBase_slo_name() {
        return base_slo_name;
    }

    public String getSrp() {
        return srp;
    }

    public String getWorkload() {
        return workload;
    }

    public String getSlo_compliance() {
        return slo_compliance;
    }

    public Integer getNum_of_vols() {
        return num_of_vols;
    }

    public Long getNum_of_child_sgs() {
        return num_of_child_sgs;
    }

    public Long getNum_of_parent_sgs() {
        return num_of_parent_sgs;
    }

    public Long getNum_of_masking_views() {
        return num_of_masking_views;
    }

    public Long getNum_of_snapshots() {
        return num_of_snapshots;
    }

    public Double getCap_gb() {
        return cap_gb;
    }

    public String getDevice_emulation() {
        return device_emulation;
    }

    public String getType() {
        return type;
    }

    public Boolean getUnprotected() {
        return unprotected;
    }

    public String[] getChild_storage_group() {
        return child_storage_group;
    }

    public String[] getParent_storage_group() {
        return parent_storage_group;
    }

    public String[] getMaskingview() {
        return maskingview;
    }

    public HostIOLimitType getHostIOLimit() {
        return hostIOLimit;
    }

    public Boolean getCompression() {
        return compression;
    }

    public String getCompressionRatio() {
        return compressionRatio;
    }

    public Double getCompression_ratio_to_one() {
        return compression_ratio_to_one;
    }

    public String getVPSaved() {
        return VPSaved;
    }
}

/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

public class StorageGroupType {

    // min/max occurs: 1/1
    private String storageGroupId;
    // min/max occurs: 0/1
    private String slo;
    // min/max occurs: 0/1
    private  String srp;
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

    public String getStorageGroupId() {
        return storageGroupId;
    }

    public void setStorageGroupId(String storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    public String getSlo() {
        return slo;
    }

    public void setSlo(String slo) {
        this.slo = slo;
    }

    public String getSrp() {
        return srp;
    }

    public void setSrp(String srp) {
        this.srp = srp;
    }

    public String getWorkload() {
        return workload;
    }

    public void setWorkload(String workload) {
        this.workload = workload;
    }

    public String getSlo_compliance() {
        return slo_compliance;
    }

    public void setSlo_compliance(String slo_compliance) {
        this.slo_compliance = slo_compliance;
    }

    public Integer getNum_of_vols() {
        return num_of_vols;
    }

    public void setNum_of_vols(Integer num_of_vols) {
        this.num_of_vols = num_of_vols;
    }

    public Long getNum_of_child_sgs() {
        return num_of_child_sgs;
    }

    public void setNum_of_child_sgs(Long num_of_child_sgs) {
        this.num_of_child_sgs = num_of_child_sgs;
    }

    public Long getNum_of_parent_sgs() {
        return num_of_parent_sgs;
    }

    public void setNum_of_parent_sgs(Long num_of_parent_sgs) {
        this.num_of_parent_sgs = num_of_parent_sgs;
    }

    public Long getNum_of_masking_views() {
        return num_of_masking_views;
    }

    public void setNum_of_masking_views(Long num_of_masking_views) {
        this.num_of_masking_views = num_of_masking_views;
    }

    public Long getNum_of_snapshots() {
        return num_of_snapshots;
    }

    public void setNum_of_snapshots(Long num_of_snapshots) {
        this.num_of_snapshots = num_of_snapshots;
    }

    public Double getCap_gb() {
        return cap_gb;
    }

    public void setCap_gb(Double cap_gb) {
        this.cap_gb = cap_gb;
    }

    public String getDevice_emulation() {
        return device_emulation;
    }

    public void setDevice_emulation(String device_emulation) {
        this.device_emulation = device_emulation;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getUnprotected() {
        return unprotected;
    }

    public void setUnprotected(Boolean unprotected) {
        this.unprotected = unprotected;
    }

    public String[] getChild_storage_group() {
        return child_storage_group;
    }

    public void setChild_storage_group(String[] child_storage_group) {
        this.child_storage_group = child_storage_group;
    }

    public String[] getParent_storage_group() {
        return parent_storage_group;
    }

    public void setParent_storage_group(String[] parent_storage_group) {
        this.parent_storage_group = parent_storage_group;
    }

    public String[] getMaskingview() {
        return maskingview;
    }

    public void setMaskingview(String[] maskingview) {
        this.maskingview = maskingview;
    }

    public HostIOLimitType getHostIOLimit() {
        return hostIOLimit;
    }

    public void setHostIOLimit(HostIOLimitType hostIOLimit) {
        this.hostIOLimit = hostIOLimit;
    }
}

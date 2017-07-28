/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.EmulationType;
import com.emc.storageos.driver.univmax.rest.type.common.ResultType;
import com.emc.storageos.driver.univmax.rest.type.common.SymmetrixPortKeyType;

public class VolumeType {

    // min/max occurs: 1/1
    private String volumeId;
    // min/max occurs: 0/1
    private String type;
    // min/max occurs: 0/1
    private EmulationType emulation;
    // min/max occurs: 0/1
    private String ssid;
    // min/max occurs: 0/1
    private Long allocated_percent;
    // min/max occurs: 0/1
    private Double cap_gb;
    // min/max occurs: 0/1
    private Double cap_mb;
    // min/max occurs: 0/1
    private Long cap_cyl;
    // min/max occurs: 0/1
    private String status;
    // min/max occurs: 0/1
    private Boolean reserved;
    // min/max occurs: 0/1
    private Boolean pinned;
    // min/max occurs: 0/1
    private String physical_name;
    // min/max occurs: 0/1
    private String volume_identifier;
    // min/max occurs: 0/1
    private String wwn;
    // min/max occurs: 0/1
    private Boolean encapsulated;
    // min/max occurs: 0/1
    private Integer num_of_storage_groups;
    // min/max occurs: 0/1
    private Long num_of_front_end_paths;
    // min/max occurs: 0/unbounded
    private String[] storageGroupId;
    // min/max occurs: 0/unbounded
    private SymmetrixPortKeyType[] symmetrixPortKey;

    public String getVolumeId() {
        return volumeId;
    }

    public void setVolumeId(String volumeId) {
        this.volumeId = volumeId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public EmulationType getEmulation() {
        return emulation;
    }

    public void setEmulation(EmulationType emulation) {
        this.emulation = emulation;
    }

    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
    }

    public Long getAllocated_percent() {
        return allocated_percent;
    }

    public void setAllocated_percent(Long allocated_percent) {
        this.allocated_percent = allocated_percent;
    }

    public Double getCap_gb() {
        return cap_gb;
    }

    public void setCap_gb(Double cap_gb) {
        this.cap_gb = cap_gb;
    }

    public Double getCap_mb() {
        return cap_mb;
    }

    public void setCap_mb(Double cap_mb) {
        this.cap_mb = cap_mb;
    }

    public Long getCap_cyl() {
        return cap_cyl;
    }

    public void setCap_cyl(Long cap_cyl) {
        this.cap_cyl = cap_cyl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getReserved() {
        return reserved;
    }

    public void setReserved(Boolean reserved) {
        this.reserved = reserved;
    }

    public Boolean getPinned() {
        return pinned;
    }

    public void setPinned(Boolean pinned) {
        this.pinned = pinned;
    }

    public String getPhysical_name() {
        return physical_name;
    }

    public void setPhysical_name(String physical_name) {
        this.physical_name = physical_name;
    }

    public String getVolume_identifier() {
        return volume_identifier;
    }

    public void setVolume_identifier(String volume_identifier) {
        this.volume_identifier = volume_identifier;
    }

    public String getWwn() {
        return wwn;
    }

    public void setWwn(String wwn) {
        this.wwn = wwn;
    }

    public Boolean getEncapsulated() {
        return encapsulated;
    }

    public void setEncapsulated(Boolean encapsulated) {
        this.encapsulated = encapsulated;
    }

    public Integer getNum_of_storage_groups() {
        return num_of_storage_groups;
    }

    public void setNum_of_storage_groups(Integer num_of_storage_groups) {
        this.num_of_storage_groups = num_of_storage_groups;
    }

    public Long getNum_of_front_end_paths() {
        return num_of_front_end_paths;
    }

    public void setNum_of_front_end_paths(Long num_of_front_end_paths) {
        this.num_of_front_end_paths = num_of_front_end_paths;
    }

    public String[] getStorageGroupId() {
        return storageGroupId;
    }

    public void setStorageGroupId(String[] storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    public SymmetrixPortKeyType[] getSymmetrixPortKey() {
        return symmetrixPortKey;
    }

    public void setSymmetrixPortKey(SymmetrixPortKeyType[] symmetrixPortKey) {
        this.symmetrixPortKey = symmetrixPortKey;
    }
}

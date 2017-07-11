/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.restvmax.vmax.type;

public class VolumeType extends ResultType {

    private String volumeId;
    private String type;
    private EmulationType emulation;
    private String ssid;
    private Long allocated_percent;
    private Double cap_gb;
    private Double cap_mb;
    private Long cap_cyl;
    private String status;
    private Boolean reserved;
    private Boolean pinned;
    private String physical_name;
    private String volume_identifier;
    private String wwn;
    private Boolean encapsulated;
    private Integer num_of_storage_groups;
    private Long num_of_front_end_paths;
    private String[] storageGroupId;
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

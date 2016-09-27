/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest.response;

/**
 * Java bean class for "/univmax/restapi/sloprovisioning/symmetrix/{}/volume/()"
 * GET method JSON result deserialization.
 *
 * Created by gang on 6/30/16.
 */
public class Volume {
    private Boolean pinned;
    private String physical_name;
    private SymmetrixPortKey symmetrixPortKey;
    private Integer allocated_percent;
    private String emulation;
    private Integer num_of_front_end_paths;
    private String type;
    private Integer cap_cyl;
    private String ssid;
    private String volume_identifier;
    private String wwn;
    private Double cap_gb;
    private Boolean reserved;
    private Boolean encapsulated;
    private Integer num_of_storage_groups;
    private String volumeId;
    private Double cap_mb;
    private String status;

    @Override
    public String toString() {
        return "Volume{" +
            "pinned=" + pinned +
            ", physical_name='" + physical_name + '\'' +
            ", symmetrixPortKey=" + symmetrixPortKey +
            ", allocated_percent=" + allocated_percent +
            ", emulation='" + emulation + '\'' +
            ", num_of_front_end_paths=" + num_of_front_end_paths +
            ", type='" + type + '\'' +
            ", cap_cyl=" + cap_cyl +
            ", ssid='" + ssid + '\'' +
            ", volume_identifier='" + volume_identifier + '\'' +
            ", wwn='" + wwn + '\'' +
            ", cap_gb=" + cap_gb +
            ", reserved=" + reserved +
            ", encapsulated=" + encapsulated +
            ", num_of_storage_groups=" + num_of_storage_groups +
            ", volumeId='" + volumeId + '\'' +
            ", cap_mb=" + cap_mb +
            ", status='" + status + '\'' +
            '}';
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

    public SymmetrixPortKey getSymmetrixPortKey() {
        return symmetrixPortKey;
    }

    public void setSymmetrixPortKey(SymmetrixPortKey symmetrixPortKey) {
        this.symmetrixPortKey = symmetrixPortKey;
    }

    public Integer getAllocated_percent() {
        return allocated_percent;
    }

    public void setAllocated_percent(Integer allocated_percent) {
        this.allocated_percent = allocated_percent;
    }

    public String getEmulation() {
        return emulation;
    }

    public void setEmulation(String emulation) {
        this.emulation = emulation;
    }

    public Integer getNum_of_front_end_paths() {
        return num_of_front_end_paths;
    }

    public void setNum_of_front_end_paths(Integer num_of_front_end_paths) {
        this.num_of_front_end_paths = num_of_front_end_paths;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getCap_cyl() {
        return cap_cyl;
    }

    public void setCap_cyl(Integer cap_cyl) {
        this.cap_cyl = cap_cyl;
    }

    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
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

    public Double getCap_gb() {
        return cap_gb;
    }

    public void setCap_gb(Double cap_gb) {
        this.cap_gb = cap_gb;
    }

    public Boolean getReserved() {
        return reserved;
    }

    public void setReserved(Boolean reserved) {
        this.reserved = reserved;
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

    public String getVolumeId() {
        return volumeId;
    }

    public void setVolumeId(String volumeId) {
        this.volumeId = volumeId;
    }

    public Double getCap_mb() {
        return cap_mb;
    }

    public void setCap_mb(Double cap_mb) {
        this.cap_mb = cap_mb;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

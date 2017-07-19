/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume.model;

import java.util.List;

import com.emc.storageos.driver.vmax3.smc.basetype.DefaultResponse;

/**
 * @author fengs5
 *
 */
public class VolumeType extends DefaultResponse {
    private String volumeId;
    private String type;
    private String emulation;
    private String ssid;
    private long allocated_percent;
    private double cap_gb;
    private double cap_mb;
    private long cap_cyl;
    private String status;
    private boolean reserved;
    private boolean pinned;
    private String physical_name;
    private String volume_identifier;
    private String wwn;
    private boolean encapsulated;
    private int num_of_storage_groups;
    private long num_of_front_end_paths;
    private List<String> storageGroupId;
    private boolean snapvx_source;
    private boolean snapvx_target;
    private String cu_image_base_address;
    private boolean has_effective_wwn;
    private String effective_wwn;
    private String encapsulated_wwn;
    private SymmetrixPortKeyType symmetrixPortKey;
    private RdfGroupIdType rdfGroupId;

    /**
     * @return the volumeId
     */
    public String getVolumeId() {
        return volumeId;
    }

    /**
     * @param volumeId the volumeId to set
     */
    public void setVolumeId(String volumeId) {
        this.volumeId = volumeId;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return the emulation
     */
    public String getEmulation() {
        return emulation;
    }

    /**
     * @param emulation the emulation to set
     */
    public void setEmulation(String emulation) {
        this.emulation = emulation;
    }

    /**
     * @return the ssid
     */
    public String getSsid() {
        return ssid;
    }

    /**
     * @param ssid the ssid to set
     */
    public void setSsid(String ssid) {
        this.ssid = ssid;
    }

    /**
     * @return the allocated_percent
     */
    public long getAllocated_percent() {
        return allocated_percent;
    }

    /**
     * @param allocated_percent the allocated_percent to set
     */
    public void setAllocated_percent(long allocated_percent) {
        this.allocated_percent = allocated_percent;
    }

    /**
     * @return the cap_gb
     */
    public double getCap_gb() {
        return cap_gb;
    }

    /**
     * @param cap_gb the cap_gb to set
     */
    public void setCap_gb(double cap_gb) {
        this.cap_gb = cap_gb;
    }

    /**
     * @return the cap_mb
     */
    public double getCap_mb() {
        return cap_mb;
    }

    /**
     * @param cap_mb the cap_mb to set
     */
    public void setCap_mb(double cap_mb) {
        this.cap_mb = cap_mb;
    }

    /**
     * @return the cap_cyl
     */
    public long getCap_cyl() {
        return cap_cyl;
    }

    /**
     * @param cap_cyl the cap_cyl to set
     */
    public void setCap_cyl(long cap_cyl) {
        this.cap_cyl = cap_cyl;
    }

    /**
     * @return the status
     */
    public String getStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * @return the reserved
     */
    public boolean isReserved() {
        return reserved;
    }

    /**
     * @param reserved the reserved to set
     */
    public void setReserved(boolean reserved) {
        this.reserved = reserved;
    }

    /**
     * @return the pinned
     */
    public boolean isPinned() {
        return pinned;
    }

    /**
     * @param pinned the pinned to set
     */
    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    /**
     * @return the physical_name
     */
    public String getPhysical_name() {
        return physical_name;
    }

    /**
     * @param physical_name the physical_name to set
     */
    public void setPhysical_name(String physical_name) {
        this.physical_name = physical_name;
    }

    /**
     * @return the volume_identifier
     */
    public String getVolume_identifier() {
        return volume_identifier;
    }

    /**
     * @param volume_identifier the volume_identifier to set
     */
    public void setVolume_identifier(String volume_identifier) {
        this.volume_identifier = volume_identifier;
    }

    /**
     * @return the wwn
     */
    public String getWwn() {
        return wwn;
    }

    /**
     * @param wwn the wwn to set
     */
    public void setWwn(String wwn) {
        this.wwn = wwn;
    }

    /**
     * @return the encapsulated
     */
    public boolean isEncapsulated() {
        return encapsulated;
    }

    /**
     * @param encapsulated the encapsulated to set
     */
    public void setEncapsulated(boolean encapsulated) {
        this.encapsulated = encapsulated;
    }

    /**
     * @return the num_of_storage_groups
     */
    public int getNum_of_storage_groups() {
        return num_of_storage_groups;
    }

    /**
     * @param num_of_storage_groups the num_of_storage_groups to set
     */
    public void setNum_of_storage_groups(int num_of_storage_groups) {
        this.num_of_storage_groups = num_of_storage_groups;
    }

    /**
     * @return the num_of_front_end_paths
     */
    public long getNum_of_front_end_paths() {
        return num_of_front_end_paths;
    }

    /**
     * @param num_of_front_end_paths the num_of_front_end_paths to set
     */
    public void setNum_of_front_end_paths(long num_of_front_end_paths) {
        this.num_of_front_end_paths = num_of_front_end_paths;
    }

    /**
     * @return the storageGroupId
     */
    public List<String> getStorageGroupId() {
        return storageGroupId;
    }

    /**
     * @param storageGroupId the storageGroupId to set
     */
    public void setStorageGroupId(List<String> storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    /**
     * @return the snapvx_source
     */
    public boolean isSnapvx_source() {
        return snapvx_source;
    }

    /**
     * @param snapvx_source the snapvx_source to set
     */
    public void setSnapvx_source(boolean snapvx_source) {
        this.snapvx_source = snapvx_source;
    }

    /**
     * @return the snapvx_target
     */
    public boolean isSnapvx_target() {
        return snapvx_target;
    }

    /**
     * @param snapvx_target the snapvx_target to set
     */
    public void setSnapvx_target(boolean snapvx_target) {
        this.snapvx_target = snapvx_target;
    }

    /**
     * @return the cu_image_base_address
     */
    public String getCu_image_base_address() {
        return cu_image_base_address;
    }

    /**
     * @param cu_image_base_address the cu_image_base_address to set
     */
    public void setCu_image_base_address(String cu_image_base_address) {
        this.cu_image_base_address = cu_image_base_address;
    }

    /**
     * @return the has_effective_wwn
     */
    public boolean isHas_effective_wwn() {
        return has_effective_wwn;
    }

    /**
     * @param has_effective_wwn the has_effective_wwn to set
     */
    public void setHas_effective_wwn(boolean has_effective_wwn) {
        this.has_effective_wwn = has_effective_wwn;
    }

    /**
     * @return the effective_wwn
     */
    public String getEffective_wwn() {
        return effective_wwn;
    }

    /**
     * @param effective_wwn the effective_wwn to set
     */
    public void setEffective_wwn(String effective_wwn) {
        this.effective_wwn = effective_wwn;
    }

    /**
     * @return the encapsulated_wwn
     */
    public String getEncapsulated_wwn() {
        return encapsulated_wwn;
    }

    /**
     * @param encapsulated_wwn the encapsulated_wwn to set
     */
    public void setEncapsulated_wwn(String encapsulated_wwn) {
        this.encapsulated_wwn = encapsulated_wwn;
    }

    /**
     * @return the symmetrixPortKey
     */
    public SymmetrixPortKeyType getSymmetrixPortKey() {
        return symmetrixPortKey;
    }

    /**
     * @param symmetrixPortKey the symmetrixPortKey to set
     */
    public void setSymmetrixPortKey(SymmetrixPortKeyType symmetrixPortKey) {
        this.symmetrixPortKey = symmetrixPortKey;
    }

    /**
     * @return the rdfGroupId
     */
    public RdfGroupIdType getRdfGroupId() {
        return rdfGroupId;
    }

    /**
     * @param rdfGroupId the rdfGroupId to set
     */
    public void setRdfGroupId(RdfGroupIdType rdfGroupId) {
        this.rdfGroupId = rdfGroupId;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "VolumeType [volumeId=" + volumeId + ", type=" + type + ", emulation=" + emulation + ", ssid=" + ssid
                + ", allocated_percent=" + allocated_percent + ", cap_gb=" + cap_gb + ", cap_mb=" + cap_mb + ", cap_cyl=" + cap_cyl
                + ", status=" + status + ", reserved=" + reserved + ", pinned=" + pinned + ", physical_name=" + physical_name
                + ", volume_identifier=" + volume_identifier + ", wwn=" + wwn + ", encapsulated=" + encapsulated
                + ", num_of_storage_groups=" + num_of_storage_groups + ", num_of_front_end_paths=" + num_of_front_end_paths
                + ", storageGroupId=" + storageGroupId + ", snapvx_source=" + snapvx_source + ", snapvx_target=" + snapvx_target
                + ", cu_image_base_address=" + cu_image_base_address + ", has_effective_wwn=" + has_effective_wwn + ", effective_wwn="
                + effective_wwn + ", encapsulated_wwn=" + encapsulated_wwn + ", symmetrixPortKey=" + symmetrixPortKey + ", rdfGroupId="
                + rdfGroupId + ", getMessage()=" + getCustMessage() + ", isSuccessfulStatus()=" + isSuccessfulStatus() + "]";
    }

}

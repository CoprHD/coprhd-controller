/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.EmulationType;
import com.emc.storageos.driver.univmax.rest.type.common.RdfGroupIdType;
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
    // min/max occurs: 0/unbounded
    private RdfGroupIdType[] rdfGroupId;
    // min/max occurs: 0/1
    private Boolean snapvx_source;
    // min/max occurs: 0/1
    private Boolean snapvx_target;
    // min/max occurs: 0/1
    private String cu_image_base_address;
    // min/max occurs: 0/1
    private Boolean has_effective_wwn;
    // min/max occurs: 0/1
    private String effective_wwn;
    // min/max occurs: 0/1
    private String encapsulated_wwn;

    public String getVolumeId() {
        return volumeId;
    }

    public String getType() {
        return type;
    }

    public EmulationType getEmulation() {
        return emulation;
    }

    public String getSsid() {
        return ssid;
    }

    public Long getAllocated_percent() {
        return allocated_percent;
    }

    public Double getCap_gb() {
        return cap_gb;
    }

    public Double getCap_mb() {
        return cap_mb;
    }

    public Long getCap_cyl() {
        return cap_cyl;
    }

    public String getStatus() {
        return status;
    }

    public Boolean getReserved() {
        return reserved;
    }

    public Boolean getPinned() {
        return pinned;
    }

    public String getPhysical_name() {
        return physical_name;
    }

    public String getVolume_identifier() {
        return volume_identifier;
    }

    public String getWwn() {
        return wwn;
    }

    public Boolean getEncapsulated() {
        return encapsulated;
    }

    public Integer getNum_of_storage_groups() {
        return num_of_storage_groups;
    }

    public Long getNum_of_front_end_paths() {
        return num_of_front_end_paths;
    }

    public String[] getStorageGroupId() {
        return storageGroupId;
    }

    public SymmetrixPortKeyType[] getSymmetrixPortKey() {
        return symmetrixPortKey;
    }

    public RdfGroupIdType[] getRdfGroupId() {
        return rdfGroupId;
    }

    public Boolean getSnapvx_source() {
        return snapvx_source;
    }

    public Boolean getSnapvx_target() {
        return snapvx_target;
    }

    public String getCu_image_base_address() {
        return cu_image_base_address;
    }

    public Boolean getHas_effective_wwn() {
        return has_effective_wwn;
    }

    public String getEffective_wwn() {
        return effective_wwn;
    }

    public String getEncapsulated_wwn() {
        return encapsulated_wwn;
    }
}

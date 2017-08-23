/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;
import com.emc.storageos.driver.univmax.rest.type.common.VolumeAttributeType;

public class SloBasedStorageGroupParamType extends ParamType {

    // min/max occurs: 0/1
    private String sloId;
    // min/max occurs: 0/1
	private String workloadSelection;
    // min/max occurs: 1/1
    private Long num_of_vols;
    // min/max occurs: 1/1
	private VolumeAttributeType volumeAttribute;
    // min/max occurs: 0/1
    private Boolean allocate_capacity_for_each_vol;
    // min/max occurs: 0/1
	private Boolean persist_preallocated_capacity_through_reclaim_or_copy;
    // min/max occurs: 0/1
    private Boolean noCompression;
    // min/max occurs: 0/1
	private VolumeIdentifierType volumeIdentifier;
    // min/max occurs: 0/1
    private SetHostIOLimitsParamType setHostIOLimitsParam;

    public void setSloId(String sloId) {
        this.sloId = sloId;
    }

    public void setWorkloadSelection(String workloadSelection) {
        this.workloadSelection = workloadSelection;
    }

    public void setNum_of_vols(Long num_of_vols) {
        this.num_of_vols = num_of_vols;
    }

    public void setVolumeAttribute(VolumeAttributeType volumeAttribute) {
        this.volumeAttribute = volumeAttribute;
    }

    public void setAllocate_capacity_for_each_vol(Boolean allocate_capacity_for_each_vol) {
        this.allocate_capacity_for_each_vol = allocate_capacity_for_each_vol;
    }

    public void setPersist_preallocated_capacity_through_reclaim_or_copy(Boolean persist_preallocated_capacity_through_reclaim_or_copy) {
        this.persist_preallocated_capacity_through_reclaim_or_copy = persist_preallocated_capacity_through_reclaim_or_copy;
    }

    public void setNoCompression(Boolean noCompression) {
        this.noCompression = noCompression;
    }

    public void setVolumeIdentifier(VolumeIdentifierType volumeIdentifier) {
        this.volumeIdentifier = volumeIdentifier;
    }

    public void setSetHostIOLimitsParam(SetHostIOLimitsParamType setHostIOLimitsParam) {
        this.setHostIOLimitsParam = setHostIOLimitsParam;
    }
}

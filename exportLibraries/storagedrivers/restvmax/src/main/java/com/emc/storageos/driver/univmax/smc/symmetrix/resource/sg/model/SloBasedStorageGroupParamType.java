/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.model;

import com.emc.storageos.driver.univmax.smc.basetype.DefaultParameter;

public class SloBasedStorageGroupParamType extends DefaultParameter {
    private String sloId;
    private String workloadSelection;
    private Long num_of_vols;
    private VolumeAttributeType volumeAttribute;
    private Boolean allocate_capacity_for_each_vol;
    private Boolean persist_preallocated_capacity_through_reclaim_or_copy;

    public SloBasedStorageGroupParamType(Long num_of_vols, VolumeAttributeType volumeAttribute) {
        this.num_of_vols = num_of_vols;
        this.volumeAttribute = volumeAttribute;
    }

    public void setSloId(String sloId) {
        this.sloId = sloId;
    }

    public void setWorkloadSelection(String workloadSelection) {
        this.workloadSelection = workloadSelection;
    }

    public void setAllocateCapacityForEachVol(boolean allocateCapacityForEachVol) {
        this.allocate_capacity_for_each_vol = allocate_capacity_for_each_vol;
    }

    public void setPersistPreallocatedCapacityThroughReclaimOrCopy(
            boolean persistPreallocatedCapacityThroughReclaimOrCopy) {
        this.persist_preallocated_capacity_through_reclaim_or_copy = persistPreallocatedCapacityThroughReclaimOrCopy;
    }
}

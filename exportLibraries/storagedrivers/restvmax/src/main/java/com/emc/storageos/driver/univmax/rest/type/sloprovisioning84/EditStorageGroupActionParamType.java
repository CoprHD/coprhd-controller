/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

public class EditStorageGroupActionParamType {

    // min/max occurs: 0/1
	private EditCompressionParamType editCompressionParam;
    // min/max occurs: 0/1
	private SetHostIOLimitsParamType setHostIOLimitsParam;
    // min/max occurs: 0/1
	private RemoveVolumeParamType removeVolumeParam;
    // min/max occurs: 0/1
	private ExpandStorageGroupParamType expandStorageGroupParam;
    // min/max occurs: 0/1
	private EditStorageGroupWorkloadParamType editStorageGroupWorkloadParam;
    // min/max occurs: 0/1
	private EditStorageGroupSLOParamType editStorageGroupSLOParam;
    // min/max occurs: 0/1
	private EditStorageGroupSRPParamType editStorageGroupSRPParam;
    // min/max occurs: 0/1
	private RemoveStorageGroupParamType removeStorageGroupParam;
    // min/max occurs: 0/1
	private RenameStorageGroupParamType renameStorageGroupParam;

    public void setEditCompressionParam(EditCompressionParamType editCompressionParam) {
        this.editCompressionParam = editCompressionParam;
    }

    public void setSetHostIOLimitsParam(SetHostIOLimitsParamType setHostIOLimitsParam) {
        this.setHostIOLimitsParam = setHostIOLimitsParam;
    }

    public void setRemoveVolumeParam(RemoveVolumeParamType removeVolumeParam) {
        this.removeVolumeParam = removeVolumeParam;
    }

    public void setExpandStorageGroupParam(ExpandStorageGroupParamType expandStorageGroupParam) {
        this.expandStorageGroupParam = expandStorageGroupParam;
    }

    public void setEditStorageGroupWorkloadParam(EditStorageGroupWorkloadParamType editStorageGroupWorkloadParam) {
        this.editStorageGroupWorkloadParam = editStorageGroupWorkloadParam;
    }

    public void setEditStorageGroupSLOParam(EditStorageGroupSLOParamType editStorageGroupSLOParam) {
        this.editStorageGroupSLOParam = editStorageGroupSLOParam;
    }

    public void setEditStorageGroupSRPParam(EditStorageGroupSRPParamType editStorageGroupSRPParam) {
        this.editStorageGroupSRPParam = editStorageGroupSRPParam;
    }

    public void setRemoveStorageGroupParam(RemoveStorageGroupParamType removeStorageGroupParam) {
        this.removeStorageGroupParam = removeStorageGroupParam;
    }

    public void setRenameStorageGroupParam(RenameStorageGroupParamType renameStorageGroupParam) {
        this.renameStorageGroupParam = renameStorageGroupParam;
    }
}

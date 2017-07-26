/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.model;

import com.emc.storageos.driver.univmax.smc.basetype.DefaultParameter;

public class EditStorageGroupActionParam extends DefaultParameter {

    private EditStorageGroupSLOParam editStorageGroupSLOParam;
    private EditStorageGroupWorkloadParam editStorageGroupWorkloadParam;
    private SetHostIOLimitsParam setHostIOLimitsParam;
    private ExpandStorageGroupParam expandStorageGroupParam;
    private RemoveVolumeParamType removeVolumeParam;

    /**
     * @return the editStorageGroupSLOParam
     */
    public EditStorageGroupSLOParam getEditStorageGroupSLOParam() {
        return editStorageGroupSLOParam;
    }

    /**
     * @param editStorageGroupSLOParam the editStorageGroupSLOParam to set
     */
    public void setEditStorageGroupSLOParam(EditStorageGroupSLOParam editStorageGroupSLOParam) {
        this.editStorageGroupSLOParam = editStorageGroupSLOParam;
    }

    /**
     * @return the editStorageGroupWorkloadParam
     */
    public EditStorageGroupWorkloadParam getEditStorageGroupWorkloadParam() {
        return editStorageGroupWorkloadParam;
    }

    /**
     * @param editStorageGroupWorkloadParam the editStorageGroupWorkloadParam to set
     */
    public void setEditStorageGroupWorkloadParam(EditStorageGroupWorkloadParam editStorageGroupWorkloadParam) {
        this.editStorageGroupWorkloadParam = editStorageGroupWorkloadParam;
    }

    /**
     * @return the setHostIOLimitsParam
     */
    public SetHostIOLimitsParam getSetHostIOLimitsParam() {
        return setHostIOLimitsParam;
    }

    /**
     * @param setHostIOLimitsParam the setHostIOLimitsParam to set
     */
    public void setSetHostIOLimitsParam(SetHostIOLimitsParam setHostIOLimitsParam) {
        this.setHostIOLimitsParam = setHostIOLimitsParam;
    }

    /**
     * @return the expandStorageGroupParam
     */
    public ExpandStorageGroupParam getExpandStorageGroupParam() {
        return expandStorageGroupParam;
    }

    /**
     * @param expandStorageGroupParam the expandStorageGroupParam to set
     */
    public void setExpandStorageGroupParam(ExpandStorageGroupParam expandStorageGroupParam) {
        this.expandStorageGroupParam = expandStorageGroupParam;
    }

    /**
     * @return the removeVolumeParam
     */
    public RemoveVolumeParamType getRemoveVolumeParam() {
        return removeVolumeParam;
    }

    /**
     * @param removeVolumeParam the removeVolumeParam to set
     */
    public void setRemoveVolumeParam(RemoveVolumeParamType removeVolumeParam) {
        this.removeVolumeParam = removeVolumeParam;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "EditStorageGroupActionParam [editStorageGroupSLOParam=" + editStorageGroupSLOParam + ", editStorageGroupWorkloadParam="
                + editStorageGroupWorkloadParam + ", setHostIOLimitsParam=" + setHostIOLimitsParam + ", expandStorageGroupParam="
                + expandStorageGroupParam + ", removeVolumeParam=" + removeVolumeParam + "]";
    }

}

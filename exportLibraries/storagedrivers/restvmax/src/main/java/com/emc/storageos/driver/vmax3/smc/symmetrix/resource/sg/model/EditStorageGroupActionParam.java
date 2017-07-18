/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model;

import com.emc.storageos.driver.vmax3.smc.basetype.AbstractParameter;

public class EditStorageGroupActionParam extends AbstractParameter {

    private EditStorageGroupSLOParam editStorageGroupSLOParam;
    private EditStorageGroupWorkloadParam editStorageGroupWorkloadParam;
    private SetHostIOLimitsParam setHostIOLimitsParam;
    private ExpandStorageGroupParam expandStorageGroupParam;

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

}

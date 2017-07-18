/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model;

import com.emc.storageos.driver.vmax3.smc.basetype.AbstractParameter;

/**
 * @author fengs5
 *
 */
public class EditStorageGroupWorkloadParam extends AbstractParameter {

    private String workloadSelection;

    /**
     * @return the workloadSelection
     */
    public String getWorkloadSelection() {
        return workloadSelection;
    }

    /**
     * @param workloadSelection the workloadSelection to set
     */
    public void setWorkloadSelection(String workloadSelection) {
        this.workloadSelection = workloadSelection;
    }

    /**
     * @param workloadSelection
     */
    public EditStorageGroupWorkloadParam(String workloadSelection) {
        super();
        this.workloadSelection = workloadSelection;
    }

}

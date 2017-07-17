/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.bean;

import com.emc.storageos.driver.vmax3.smc.basetype.AbstractParameter;

public class EditStorageGroupSLOParam extends AbstractParameter {
    private String sloId;

    public String getSloId() {
        return sloId;
    }

    public void setSloId(String sloId) {
        this.sloId = sloId;
    }

    /**
     * @param sloId
     */
    public EditStorageGroupSLOParam(String sloId) {
        super();
        this.sloId = sloId;
    }

}

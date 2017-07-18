/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model;

import com.emc.storageos.driver.vmax3.smc.basetype.AbstractParameter;

public class EditStorageGroupParameter extends AbstractParameter {
    private EditStorageGroupActionParam editStorageGroupActionParam;

    /**
     * @return the editStorageGroupActionParam
     */
    public EditStorageGroupActionParam getEditStorageGroupActionParam() {
        return editStorageGroupActionParam;
    }

    /**
     * @param editStorageGroupActionParam the editStorageGroupActionParam to set
     */
    public void setEditStorageGroupActionParam(EditStorageGroupActionParam editStorageGroupActionParam) {
        this.editStorageGroupActionParam = editStorageGroupActionParam;
    }

}

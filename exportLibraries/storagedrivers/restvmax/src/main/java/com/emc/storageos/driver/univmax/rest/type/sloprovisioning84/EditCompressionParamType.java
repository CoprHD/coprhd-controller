/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class EditCompressionParamType extends ParamType {

    // min/max occurs: 0/1
    private Boolean compression;

    public void setCompression(Boolean compression) {
        this.compression = compression;
    }
}

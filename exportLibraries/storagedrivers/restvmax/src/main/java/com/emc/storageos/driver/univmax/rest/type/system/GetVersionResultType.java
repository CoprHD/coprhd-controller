/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.system;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class GetVersionResultType extends ParamType {

    // min/max occurs: 1/1
    private String version;

    public String getVersion() {
        return version;
    }
}

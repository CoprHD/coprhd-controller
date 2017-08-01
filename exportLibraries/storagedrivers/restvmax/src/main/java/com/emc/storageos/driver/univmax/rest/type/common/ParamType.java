/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.common;

import com.emc.storageos.driver.univmax.rest.JsonUtil;

public class ParamType {
    public String toJsonString() {
        return JsonUtil.toJsonString(this);
    }
}

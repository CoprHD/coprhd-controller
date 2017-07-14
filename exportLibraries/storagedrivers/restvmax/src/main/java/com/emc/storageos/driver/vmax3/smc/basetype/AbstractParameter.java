/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.basetype;

import com.google.gson.Gson;

public abstract class AbstractParameter implements IParameter {

    @Override
    public String bean2Json() {
        return new Gson().toJson(this);
    }
}

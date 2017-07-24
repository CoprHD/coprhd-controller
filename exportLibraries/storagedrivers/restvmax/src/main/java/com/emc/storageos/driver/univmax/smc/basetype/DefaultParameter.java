/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.smc.basetype;

import com.emc.storageos.driver.univmax.utils.TranslationTool;
import com.google.gson.Gson;

public class DefaultParameter implements IParameter {

    @Override
    public String bean2Json() {
        return new Gson().toJson(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.driver.vmax3.smc.basetype.IParameter#fromViprPojo(java.lang.Object)
     */
    @Override
    public void fromViprPojo(Object viprPojo) {
        TranslationTool.fromViprPojo(this, viprPojo);

    }

}

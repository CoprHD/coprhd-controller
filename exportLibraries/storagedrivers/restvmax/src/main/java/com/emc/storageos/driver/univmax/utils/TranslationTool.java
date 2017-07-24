/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.utils;

import com.emc.storageos.driver.univmax.smc.basetype.IParameter;
import com.emc.storageos.driver.univmax.smc.basetype.IResponse;

/**
 * @author fengs5
 *
 */
public class TranslationTool {

    /**
     * 
     */
    private TranslationTool() {
    }

    public static <T> T toViprPojo(IResponse responsePojo, Class<T> clazz) {
        // Parse response Pojo to find the annotation TargetFieldName for field volumeId
        // and fill its value to field nativeId in target Pojo<Volume> and return the target Pojo.
        return null;
    }

    public static void fromViprPojo(IParameter parameterPojo, Object viprPojo) {

        // Parse parameterPojo to find the annotation SourceFieldName and set it with value from viprPojo
    }

}

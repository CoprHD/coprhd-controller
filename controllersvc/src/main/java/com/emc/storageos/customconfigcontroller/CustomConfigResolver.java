/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.customconfigcontroller;

import java.io.Serializable;

import com.emc.storageos.db.client.model.StringMap;

public abstract class  CustomConfigResolver implements Serializable{
    
    /**
     * Validate if the value is valid
     * @param configTemplate the config template
     * @param scope the scope of the config
     * @param value the config value
     */
    public abstract void validate(CustomConfigType configTemplate, StringMap scope, String value);
    
    /**
     * Resolve the value using the datasource
     * @param configTemplate the config template
     * @param scope the scope of the config
     * @param value
     * @param datasource
     * @return resolved value
     */
    public abstract String resolve(CustomConfigType configTemplate, StringMap scope, String value, DataSource datasource);
}

/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

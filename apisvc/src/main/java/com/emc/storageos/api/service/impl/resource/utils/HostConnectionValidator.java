/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.util.Map;

import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Host.HostType;
import com.emc.storageos.model.host.HostParam;
import com.google.common.collect.Maps;

public abstract class HostConnectionValidator {
    
    protected static Map<HostType, HostConnectionValidator> validators = Maps.newHashMap();
    
    protected static void addValidator(HostConnectionValidator hostConnectionValidator) {
        validators.put(hostConnectionValidator.getType(), hostConnectionValidator);
    }
    
    public static boolean isHostConnectionValid(HostParam hostParam, Host existingHost) {
        HostType hostType = HostType.valueOf(hostParam.getType());
        
        HostConnectionValidator hostConnectionValidator = validators.get(hostType);
        if (hostConnectionValidator != null) {
            return hostConnectionValidator.validateConnection(hostParam, existingHost);
        }

        return true;
    }
    
    public abstract HostType getType();
    
    public abstract boolean validateConnection(HostParam hostParam, Host existingHost);   
    
}

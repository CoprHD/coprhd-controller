/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;

import com.emc.storageos.db.client.model.UserPreferences;
import com.emc.vipr.model.catalog.UserPreferencesRestRep;
import com.emc.vipr.model.catalog.UserPreferencesUpdateParam;

public class UserPreferencesMapper {

    public static UserPreferencesRestRep map(UserPreferences from) {
        if (from == null) {
            return null;
        }
        UserPreferencesRestRep to = new UserPreferencesRestRep();

        mapDataObjectFields(from, to);
        
        to.setUsername(from.getUserId());
        to.setNotifyByEmail(from.getNotifyByEmail());
        to.setEmail(from.getEmail());
        
        return to;
    }    
    
    public static void updateObject(UserPreferences object, UserPreferencesUpdateParam param) {
        if (param.getNotifyByEmail() != null) {
            object.setNotifyByEmail(param.getNotifyByEmail());
        }
        if (param.getEmail() != null) {
            object.setEmail(param.getEmail());
        }
    }       

}

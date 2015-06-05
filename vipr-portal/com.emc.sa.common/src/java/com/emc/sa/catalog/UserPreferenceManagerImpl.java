/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.catalog;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.model.UserPreferences;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.security.authentication.StorageOSUser;

@Component
public class UserPreferenceManagerImpl implements UserPreferenceManager {

    private static final Logger log = Logger.getLogger(UserPreferenceManagerImpl.class);
    
    private static final boolean DEFAULT_NOTIFY_BY_EMAIL = false;
    private static final String DEFAULT_EMAIL = null;
    
    @Autowired
    private ModelClient client;
    
    public UserPreferences getPreferences(StorageOSUser user) {
        return getPreferences(user.getUserName());
    }
    
    public UserPreferences getPreferences(String userName) {
        UserPreferences preferences = client.preferences().findByUserId(userName);
        if (preferences == null) {
            preferences = new UserPreferences();
            preferences.setNotifyByEmail(DEFAULT_NOTIFY_BY_EMAIL);
            preferences.setEmail(DEFAULT_EMAIL);
            preferences.setUserId(userName);
            client.save(preferences);
        }
        return preferences;
    }    
    
    public void updatePreferences(UserPreferences userPreferences) {
        client.save(userPreferences);
    }

}

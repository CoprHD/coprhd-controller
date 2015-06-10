/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import com.emc.storageos.db.client.model.UserPreferences;
import com.emc.storageos.security.authentication.StorageOSUser;

public interface UserPreferenceManager {

    public UserPreferences getPreferences(StorageOSUser user);

    public UserPreferences getPreferences(String userName);

    public void updatePreferences(UserPreferences userPreferences);

}

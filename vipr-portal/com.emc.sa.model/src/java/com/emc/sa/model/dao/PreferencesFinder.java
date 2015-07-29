/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.UserPreferences;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;

public class PreferencesFinder extends ModelFinder<UserPreferences> {

    public PreferencesFinder(DBClientWrapper client) {
        super(UserPreferences.class, client);
    }

    public UserPreferences findByUserId(String userId) {
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        final List<NamedElement> userPrefsIds = client.findByAlternateId(UserPreferences.class, UserPreferences.USER_ID, userId);
        final List<UserPreferences> userPrefs = findByIds(toURIs(userPrefsIds));
        if (userPrefs.size() > 1) {
            throw new IllegalStateException("There should only be 1 user preferences object for a user");
        }
        else if (userPrefs.size() == 0) {
            // if there isn't a user prefs object in the DB yet then we haven't saved one for this user yet.
            return null;
        }
        return userPrefs.get(0);
    }
}

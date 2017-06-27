/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.catalog

import static com.emc.vipr.sanity.Sanity.*
import static org.junit.Assert.*

import com.emc.vipr.model.catalog.UserPreferencesRestRep
import com.emc.vipr.model.catalog.UserPreferencesUpdateParam

class UserPreferencesServiceHelper {

    private static boolean existingPreferences = false
    private static Boolean existingNotifyByEmail
    private static String existingEmail

    static void userPreferencesServiceTest() {
        println "  ## User Preferences Service Test ## "

        UserPreferencesRestRep preferences = catalog.userPreferences().getPreferences();

        assertNotNull("User Preferences are not null", preferences)

        existingPreferences = true
        existingNotifyByEmail = preferences.getNotifyByEmail()
        existingEmail = preferences.getEmail()

        UserPreferencesUpdateParam updateParam = new UserPreferencesUpdateParam()
        updateParam.setNotifyByEmail(true)
        updateParam.setEmail("test@test.com")
        catalog.userPreferences().updatePreferences(updateParam);

        UserPreferencesRestRep updatedPreferences = catalog.userPreferences().getPreferences();

        assertNotNull("Updated Catalog Prerferences are not null", updatedPreferences)
        assertEquals("Notify By Email was updated and returned correctly", true, updatedPreferences.getNotifyByEmail())
        assertEquals("Email was updated and returned correctly", "test@test.com", updatedPreferences.getEmail())
    }

    static void userPreferencesServiceTearDown() {
        println "  ## User Preferences Service Test Clean up ## "

        if (existingPreferences) {
            UserPreferencesRestRep preferences = catalog.userPreferences().getPreferences()
            UserPreferencesUpdateParam updateParam = new UserPreferencesUpdateParam()
            updateParam.setNotifyByEmail(existingNotifyByEmail)
            updateParam.setEmail(existingEmail)
            catalog.userPreferences().updatePreferences(updateParam);
        }
    }
}

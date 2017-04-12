/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.catalog

import static com.emc.vipr.sanity.Sanity.*
import static org.junit.Assert.*

import com.emc.vipr.model.catalog.CatalogPreferencesRestRep
import com.emc.vipr.model.catalog.CatalogPreferencesUpdateParam

class CatalogPreferencesServiceHelper {

    private static boolean existingPreferences = false
    private static String existingApprovalUrl
    private static String existingApproverEmail

    static void catalogPreferencesServiceTest() {
        println "  ## Catalog Preferences Service Test ## "

        CatalogPreferencesRestRep preferences = catalog.catalogPreferences().getPreferences();

        assertNotNull("Catalog Preferences are not null", preferences)

        existingPreferences = true
        existingApprovalUrl = preferences.getApprovalUrl()
        existingApproverEmail = preferences.getApproverEmail()

        CatalogPreferencesUpdateParam updateParam = new CatalogPreferencesUpdateParam()
        updateParam.setApprovalUrl("https://localhost/bla")
        updateParam.setApproverEmail("test@test.com")
        catalog.catalogPreferences().updatePreferences(updateParam);

        CatalogPreferencesRestRep updatedPreferences = catalog.catalogPreferences().getPreferences();

        assertNotNull("Updated Catalog Prerferences are not null", updatedPreferences)
        assertEquals("Approval URL was updated and returned correctly", "https://localhost/bla", updatedPreferences.getApprovalUrl())
        assertEquals("Approver Email was updated and returned correctly", "test@test.com", updatedPreferences.getApproverEmail())
    }

    static void catalogPreferencesServiceTearDown() {
        println "  ## Catalog Preferences Service Test Clean up ## "

        if (existingPreferences) {
            CatalogPreferencesRestRep preferences = catalog.catalogPreferences().getPreferences()
            CatalogPreferencesUpdateParam updateParam = new CatalogPreferencesUpdateParam()
            updateParam.setApprovalUrl(existingApprovalUrl)
            updateParam.setApproverEmail(existingApproverEmail)
            catalog.catalogPreferences().updatePreferences(updateParam);
        }
    }
}

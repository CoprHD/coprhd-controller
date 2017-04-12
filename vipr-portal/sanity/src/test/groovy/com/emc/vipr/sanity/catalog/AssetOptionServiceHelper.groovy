/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.catalog

import static com.emc.vipr.sanity.Sanity.*
import static org.junit.Assert.*

import com.emc.vipr.model.catalog.AssetOption

class AssetOptionServiceHelper {

    private static final String AO_PROJECT = "vipr.project"
    private static final String AO_TARGET_VIRTUAL_POOL = "vipr.targetVirtualPool"
    private static final String AO_SOURCE_BLOCK_VOLUME = "vipr.sourceBlockVolume"
    private static final String AO_VIRTUAL_POOL_CHANGE_OPERATION = "vipr.virtualPoolChangeOperation";
    private static final String AO_INVALID = "invalid.rainbow.dash"

    static void assetOptionServiceTest() {

        println "  ## Asset Options Service Test ## "

        def params = [:]

        List<AssetOption> assetOptions = catalog.assetOptions().getAssetOptions(AO_PROJECT, params)

        assertNotNull("AssetOptions for " + AO_PROJECT + " are not null", assetOptions)
        assertTrue("At least one asset option is returned for " + AO_PROJECT, assetOptions.size() > 0)

        List<AssetOption> invalidAssetOptions = catalog.assetOptions().getAssetOptions(AO_INVALID, params)

        assertNotNull("AssetOptions for " + AO_INVALID + " are not null", invalidAssetOptions)
        assertTrue("Zeror asset options are returned for " + AO_INVALID, invalidAssetOptions.size() == 0)

        Set availableAssetTypes = [
            AO_SOURCE_BLOCK_VOLUME,
            AO_VIRTUAL_POOL_CHANGE_OPERATION,
            AO_PROJECT] as Set

        List<String> dependencies = catalog.assetOptions().getAssetDependencies(AO_TARGET_VIRTUAL_POOL, availableAssetTypes)

        assertNotNull("Asset Dependencies for " + AO_TARGET_VIRTUAL_POOL + " are not null", dependencies)
        assertEquals("Number of asset dependencies returned for " + AO_TARGET_VIRTUAL_POOL, 2, dependencies.size())
    }

    static void assetOptionServiceTearDown() {
        println "  ## Asset Opitons Service Test Clean up ## "
    }
}

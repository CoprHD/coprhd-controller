/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.catalog

import static com.emc.vipr.sanity.Sanity.*
import static org.junit.Assert.*

import com.emc.vipr.model.catalog.AssetOption
import com.emc.vipr.model.catalog.AssetOptionsRequest
import java.net.URI

class RemoteReplicationHelper {

    static final String RR_VPOOL = "SBSDK_VPOOL_RR"

    // constants for asset option types
    static final AO_RR_STORAGE_TYPE = "vipr.storageSystemType"
    static final AO_RR_SETS_FOR_TYPE = "vipr.remoteReplicationSetsForArrayType" // depends on 'storageSystemType'
    static final AO_RR_GROUPS_FOR_SET = "vipr.remoteReplicationGroupForSet"  // depends on 'remoteReplicationSetsForArrayType'
    static final AO_RR_CG_OR_PAIR = "vipr.remoteReplicationCgOrPair"
    static final AO_RR_PAIRS_OR_CGS = "vipr.remoteReplicationPairsOrCGs" // depends on "remoteReplicationSetsForArrayType","remoteReplicationGroupForSet","remoteReplicationCgOrPair"
    static final AO_VARRAY = "vipr.blockVirtualArray"
    static final AO_VPOOL = "vipr.blockVirtualPool"
    static final AO_PROJECT = "vipr.project"

    // constants for expected resources made by sbsdk sanity tests
    static final RR_DRIVER_TYPE = "DRIVERSYSTEM"
    static final RR_SET = "replicationSet1 [ACTIVE]"
    static final RR_GROUP = "replicationGroup1_set1 [ACTIVE] (synchronous)"

    // global fields
    static String RR_DRIVER_TYPE_ID
    static String RR_SET_ID
    static String RR_GROUP_ID
    static URI tenantId

    static boolean topologyLoadedTest() {
        def vpoolId = client.blockVpools().search().byExactName(RR_VPOOL).first()?.id
        if (vpoolId == null) {
            println "Required topology does not exist"
            return false;
        }
        println "Required topology exists"
        return true;
        // TODO: add more checks to confirm topology is loaded
    }

    static void storageTypeAssetOptionTest() { 
        println "Testing Asset Option Provider for Storage System Type"
        List<AssetOption> assetOptions = getOptions(AO_RR_STORAGE_TYPE)
        RR_DRIVER_TYPE_ID = optionKeyForValue(assetOptions,RR_DRIVER_TYPE)
        assertNotNull("AssetOptions for " + AO_RR_STORAGE_TYPE + " are not null", assetOptions)
        assertTrue("At least one asset option is returned for " + AO_RR_STORAGE_TYPE, assetOptions.size() > 0)
        assertTrue("Storage types contain " + RR_DRIVER_TYPE + " in " + assetOptions, optionsContainValue(assetOptions,RR_DRIVER_TYPE))
    }

    static void rrSetsAssetOptionTest() {  // depends on storageTypeAssetOptionTest()
        println "Testing Asset Option Provider for RR Sets for storage type"

        Set availableAssetTypes = [AO_RR_STORAGE_TYPE] as Set
        List<String> dependencies = catalog.assetOptions().getAssetDependencies(AO_RR_SETS_FOR_TYPE, availableAssetTypes)
        assertNotNull("Asset Dependencies for " + AO_RR_SETS_FOR_TYPE + " are not null", dependencies)
        assertEquals("Number of asset dependencies returned for " + AO_RR_SETS_FOR_TYPE, 1, dependencies.size())

        def params = [(AO_RR_STORAGE_TYPE):RR_DRIVER_TYPE_ID]
        List<AssetOption> assetOptions = getOptions(AO_RR_SETS_FOR_TYPE,params)
        RR_SET_ID = optionKeyForValue(assetOptions,RR_SET)
        assertNotNull("AssetOptions for " + AO_RR_SETS_FOR_TYPE + " are not null", assetOptions)
        assertTrue("At least one asset option is returned for " + AO_RR_SETS_FOR_TYPE, assetOptions.size() > 0)
        assertTrue("RR Sets contains " + RR_SET + " in " + assetOptions, optionsContainValue(assetOptions,RR_SET))
    }

    static void rrGroupsForSetAssetOptionTest() {   // depends on rrSetsAssetOptionTest()
        println "Testing Asset Option Provider for RR Groups for RR Set"

        Set availableAssetTypes = [AO_RR_SETS_FOR_TYPE] as Set
        List<String> dependencies = catalog.assetOptions().getAssetDependencies(AO_RR_GROUPS_FOR_SET, availableAssetTypes)
        assertNotNull("Asset Dependencies for " + AO_RR_GROUPS_FOR_SET + " are not null", dependencies)
        assertEquals("Number of asset dependencies returned for " + AO_RR_GROUPS_FOR_SET, 1, dependencies.size())

        def params = [(AO_RR_SETS_FOR_TYPE):RR_SET_ID]
        List<AssetOption> assetOptions = getOptions(AO_RR_GROUPS_FOR_SET,params)
        RR_GROUP_ID = optionKeyForValue(assetOptions,RR_GROUP)
        assertNotNull("AssetOptions for " + AO_RR_GROUPS_FOR_SET + " are not null", assetOptions)
        assertTrue("At least one asset option is returned for " + AO_RR_GROUPS_FOR_SET, assetOptions.size() > 0)
        assertTrue("RR Groups contains " + RR_GROUP + " in " + assetOptions, optionsContainValue(assetOptions,RR_GROUP))
    }

	// see if asset options contains one with specific value
    static boolean optionsContainValue(List<AssetOption> assetOptions, String value) { 
        for(AssetOption assetOption : assetOptions) { 
            if(assetOption.value.equals(value)) { 
                return true
            }
        }
        return false    
    }

	// get value (ie: ID) for given assetType
    static String getOption(String assetTag, String optionName) {
        def params = [:]
        return getOption(assetTag, optionName, params)
    }

	// get value (ie: ID) for given assetType with dependencies
    static String getOption(String assetTag, String optionName, Map params) {
        return optionKeyForValue(getOptions(assetTag,params),optionName)
    }

	// get value (ie: ID) from given asset options
    static String optionKeyForValue(List<AssetOption> assetOptions, String value) {
        for(AssetOption assetOption : assetOptions) {
            if(assetOption.value.equals(value)) {
                return assetOption.key
            }
        }
        return null
    }

	// get assetOptions (ie: ID) for given assetType
    static List<AssetOption> getOptions(String assetTag) {
        def params = [:]
        return getOptions(assetTag, params)
    }

	// get assetOptions (ie: ID) for given assetType with dependencies
    static List<AssetOption> getOptions(String assetTag, Map params) {
        AssetOptionsRequest request = new AssetOptionsRequest();
        request.setTenantId(tenantId);
        request.setAvailableAssets(params);
        List<AssetOption> assetOptions = catalog.assetOptions().getAssetOptions(assetTag,request)
        return assetOptions
    }

    static setTenant(URI tenantId) {
      this.tenantId = tenantId
    }

    static URI getTenant() {
      return this.tenantId
    }
}

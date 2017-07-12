/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.catalog

//import static com.emc.vipr.sanity.Sanity.client
//import static com.emc.vipr.sanity.Sanity.catalog
import static com.emc.vipr.sanity.Sanity.*

import com.emc.vipr.model.catalog.AssetOption
//import static org.junit.Assert.assertNotNull
//import static org.junit.Assert.assertTrue
//import static org.junit.Assert.assertEqual
import static org.junit.Assert.*


class RemoteReplicationHelper {

    static final String VPOOL = "SBSDK_VPOOL"
    static def vpoolId

    private static final String AO_RR_STORAGE_TYPE = "vipr.storageSystemType"
    private static final String AO_RR_SETS_FOR_TYPE = "vipr.remoteReplicationSetsForArrayType" // depends on 'storageSystemType'
    private static final String AO_RR_GROUPS_FOR_SET = "vipr.remoteReplicationGroupForSet"  // depends on 'remoteReplicationSetsForArrayType'
    private static final String AO_RR_CG_OR_PAIR = "vipr.remoteReplicationCgOrPair"
    private static final String AO_RR_PAIRS_OR_CGS = "vipr.remoteReplicationPairsOrCGs" // depends on "remoteReplicationSetsForArrayType","remoteReplicationGroupForSet","remoteReplicationCgOrPair"

    private static final String RR_DRIVER_TYPE = "DRIVERSYSTEM"
    private static String RR_DRIVER_TYPE_ID

    private static final String RR_SET = "replicationSet1 [ACTIVE]"
    private static String RR_SET_ID

    private static final String RR_GROUP = "replicationGroup1_set1 [ACTIVE] (asynchronous)"
    private static String RR_GROUP_ID

    static boolean topologyLoadedTest() {
        vpoolId = client.blockVpools().search().byExactName(VPOOL).first()?.id
        if (vpoolId == null) {
            println "Required topology does not exist"
            return false;
        }
        println "Required topology exists"
        return true;       
    }

    static void storageTypeAssetOptionTest() { 
        println "Testing Asset Option Provider for Storage System Type"
        def params = [:]
        List<AssetOption> assetOptions = catalog.assetOptions().getAssetOptions(AO_RR_STORAGE_TYPE, params)
        RR_DRIVER_TYPE_ID = optionKeyForValue(assetOptions,RR_DRIVER_TYPE)
        assertNotNull("AssetOptions for " + AO_RR_STORAGE_TYPE + " are not null", assetOptions)
        assertTrue("At least one asset option is returned for " + AO_RR_STORAGE_TYPE, assetOptions.size() > 0)
        assertTrue("Storage types contain " + RR_DRIVER_TYPE + " in " + assetOptions, optionsContainValue(assetOptions,RR_DRIVER_TYPE))
    }

    static void rrSetsAssetOptionTest() { 
        println "Testing Asset Option Provider for RR Sets for storage type"

        Set availableAssetTypes = [AO_RR_STORAGE_TYPE] as Set
        List<String> dependencies = catalog.assetOptions().getAssetDependencies(AO_RR_SETS_FOR_TYPE, availableAssetTypes)
        assertNotNull("Asset Dependencies for " + AO_RR_SETS_FOR_TYPE + " are not null", dependencies)
        assertEquals("Number of asset dependencies returned for " + AO_RR_SETS_FOR_TYPE, 1, dependencies.size())

        def params = [(AO_RR_STORAGE_TYPE):RR_DRIVER_TYPE_ID]
        List<AssetOption> assetOptions = catalog.assetOptions().getAssetOptions(AO_RR_SETS_FOR_TYPE, params)
        RR_SET_ID = optionKeyForValue(assetOptions,RR_SET)
        assertNotNull("AssetOptions for " + AO_RR_SETS_FOR_TYPE + " are not null", assetOptions)
        assertTrue("At least one asset option is returned for " + AO_RR_SETS_FOR_TYPE, assetOptions.size() > 0)
        assertTrue("RR Sets contains " + RR_SET + " in " + assetOptions, optionsContainValue(assetOptions,RR_SET))
    }

    static void rrGroupsForSetAssetOptionTest() { 
        println "Testing Asset Option Provider for RR Groups for RR Set"

        Set availableAssetTypes = [AO_RR_SETS_FOR_TYPE] as Set
        List<String> dependencies = catalog.assetOptions().getAssetDependencies(AO_RR_GROUPS_FOR_SET, availableAssetTypes)
        assertNotNull("Asset Dependencies for " + AO_RR_GROUPS_FOR_SET + " are not null", dependencies)
        assertEquals("Number of asset dependencies returned for " + AO_RR_GROUPS_FOR_SET, 1, dependencies.size())

        def params = [(AO_RR_SETS_FOR_TYPE):RR_SET_ID]
        List<AssetOption> assetOptions = catalog.assetOptions().getAssetOptions(AO_RR_GROUPS_FOR_SET, params)
        RR_GROUP_ID = optionKeyForValue(assetOptions,RR_GROUP)
        assertNotNull("AssetOptions for " + AO_RR_GROUPS_FOR_SET + " are not null", assetOptions)
        assertTrue("At least one asset option is returned for " + AO_RR_GROUPS_FOR_SET, assetOptions.size() > 0)
        assertTrue("RR Groups contains " + RR_GROUP + " in " + assetOptions, optionsContainValue(assetOptions,RR_GROUP))
    }

    static boolean optionsContainValue(List<AssetOption> assetOptions, String value) { 
        for(AssetOption assetOption : assetOptions) { 
            if(assetOption.value.equals(value)) { 
                return true
            }
        }
        return false    
    }
    
    static String optionKeyForValue(List<AssetOption> assetOptions, String value) { 
        for(AssetOption assetOption : assetOptions) { 
            if(assetOption.value.equals(value)) { 
                return assetOption.key
            }
        }
        return null
    }
    

//    static void nextTest() {
//        Set availableAssetTypes = [
//            AO_SOURCE_BLOCK_VOLUME,
//            AO_VIRTUAL_POOL_CHANGE_OPERATION,
//            AO_PROJECT] as Set
//
//        List<String> dependencies = catalog.assetOptions().getAssetDependencies(AO_TARGET_VIRTUAL_POOL, availableAssetTypes)
//
//        assertNotNull("Asset Dependencies for " + AO_TARGET_VIRTUAL_POOL + " are not null", dependencies)
//        assertEquals("Number of asset dependencies returned for " + AO_TARGET_VIRTUAL_POOL, 2, dependencies.size())
//
//    }
    
}

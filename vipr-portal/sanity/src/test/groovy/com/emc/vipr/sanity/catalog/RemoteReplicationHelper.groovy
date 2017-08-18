/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.catalog

import static com.emc.vipr.sanity.Sanity.*
import static com.emc.vipr.sanity.setup.RemoteReplicationSetup.*
import static com.emc.vipr.sanity.catalog.CatalogServiceHelper.*
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
    static final CG_OR_PAIR_PAIR = "Remote Replication Pair"
    static final CG_OR_PAIR_CG = "Consistency Group"
    static final RR_GROUP_NONE_OPTION = "None" // none option shown in rr group menu
    static final RR_PAIR_IN_CG = "rr_vol_in_cg [INACTIVE] (synchronous)"
    static final RR_PAIR_IN_CG_IN_RR_GRP = "rr_vol_in_cg_rr_grp [INACTIVE] (synchronous)"
    static final RR_PAIR_IN_RR_GRP = "rr_vol_in_rr_grp [INACTIVE] (synchronous)"
    static final RR_PAIR_IN_RR_SET = "rr_vol_in_rr_set [INACTIVE] (synchronous)"

    // global fields for ViPR IDs discovered during testing
    static String RR_DRIVER_TYPE_ID
    static String RR_SET_ID
    static String RR_GROUP_ID
    static String RR_GROUP2_ID
    static String RR_PAIR_IN_CG_ID
    static String RR_PAIR_IN_CG_IN_RR_GRP_ID
    static String RR_PAIR_IN_RR_GRP_ID
    static String RR_PAIR_IN_RR_SET_ID
    static String CG_FOR_GRP_ID
    static String CG_FOR_SET_ID

    static URI tenantId

    static boolean topologyLoadedViprSanityTest() {
        def vpoolId = client.blockVpools().search().byExactName(RR_VPOOL).first()?.id
        if (vpoolId == null) {
            println "Required topology from ViPR SB SDK sanity does not exist"
            return false;
        }
        println "Required topology from ViPR SB SDK sanity exists"
        return true;
        // TODO: add more checks to confirm topology is loaded
    }

    static boolean topologyLoadedTest() {
        def volId = client.blockVolumes().search().byExactName(VOL_IN_SET_NAME).first()?.id
        if (volId == null) {
            println "Required topology for catalog sanity does not exist"
            return false;
        }
        println "Required topology for catalog sanity exists"
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

        // get ID for other grp while we're here
        assetOptions = getOptions(AO_RR_GROUPS_FOR_SET,params)
        RR_GROUP2_ID = optionKeyForValue(assetOptions,RR_GROUP2)
    }

    static void cgOrPairOptionTest() {
        println "Testing Asset Option Provider for CG or RR Pair Selector"
        List<AssetOption> assetOptions = getOptions(AO_RR_CG_OR_PAIR)
        assertNotNull("AssetOptions for " + AO_RR_CG_OR_PAIR + " are not null", assetOptions)
        assertTrue("Three asset option returned for " + AO_RR_CG_OR_PAIR, assetOptions.size() == 3)
        assertTrue("RR Groups contains " + CG_OR_PAIR_PAIR + " in " + assetOptions, optionsContainValue(assetOptions,CG_OR_PAIR_PAIR))
        assertTrue("RR Groups contains " + CG_OR_PAIR_CG + " in " + assetOptions, optionsContainValue(assetOptions,CG_OR_PAIR_CG))
    }

    static void cgsOrPairsDependenciesTest(){
        println "Testing Asset Option Provider dependencies for CG or Pairs provider"
        Set availableAssetTypes = [AO_RR_SETS_FOR_TYPE,AO_RR_GROUPS_FOR_SET,AO_RR_CG_OR_PAIR] as Set
        List<String> dependencies = catalog.assetOptions().getAssetDependencies(AO_RR_PAIRS_OR_CGS, availableAssetTypes)
        assertNotNull("Asset Dependencies for " + AO_RR_PAIRS_OR_CGS + " are not null", dependencies)
        assertEquals("Number of asset dependencies returned for " + AO_RR_PAIRS_OR_CGS, 3, dependencies.size())
    }

    static void cgsForSetTest(){
        println "Testing Asset Option Provider for ConsistencyGroups in RR Set"
        def params = [(AO_RR_SETS_FOR_TYPE):RR_SET_ID,(AO_RR_GROUPS_FOR_SET):RR_GROUP_NONE_OPTION,(AO_RR_CG_OR_PAIR):CG_OR_PAIR_CG]
        List<AssetOption> assetOptions = getOptions(AO_RR_PAIRS_OR_CGS,params)
        assertTrue("CGs for set contains " + CG_NAME_FOR_SET + " in " + assetOptions, optionsContainValue(assetOptions,CG_NAME_FOR_SET))
        assertTrue("CGs for set contains " + CG_NAME_FOR_GRP + " in " + assetOptions, optionsContainValue(assetOptions,CG_NAME_FOR_GRP))
        assertTrue("Two asset options returned for " + AO_RR_PAIRS_OR_CGS, assetOptions.size() == 2)

        // save for later tests
        CG_FOR_SET_ID = optionKeyForValue(assetOptions,CG_NAME_FOR_SET)
        CG_FOR_GRP_ID = optionKeyForValue(assetOptions,CG_NAME_FOR_GRP)
    }

    static void cgsForGrpTest(){
        println "Testing Asset Option Provider for ConsistencyGroups in RR Grp"
        def params = [(AO_RR_SETS_FOR_TYPE):RR_SET_ID,(AO_RR_GROUPS_FOR_SET):RR_GROUP2_ID,(AO_RR_CG_OR_PAIR):CG_OR_PAIR_CG]
        List<AssetOption> assetOptions = getOptions(AO_RR_PAIRS_OR_CGS,params)
        assertTrue("One asset option returned for " + AO_RR_PAIRS_OR_CGS, assetOptions.size() == 1)
        assertTrue("CGs for set contains " + CG_NAME_FOR_GRP + " in " + assetOptions, optionsContainValue(assetOptions,CG_NAME_FOR_GRP))
    }

    static void pairsForSetTest(){
        println "Testing Asset Option Provider for RR Pairs in RR Set"
        def params = [(AO_RR_SETS_FOR_TYPE):RR_SET_ID,(AO_RR_GROUPS_FOR_SET):RR_GROUP_NONE_OPTION,(AO_RR_CG_OR_PAIR):CG_OR_PAIR_PAIR]
        List<AssetOption> assetOptions = getOptions(AO_RR_PAIRS_OR_CGS,params)
        assertTrue("Four asset options returned for " + AO_RR_PAIRS_OR_CGS, assetOptions.size() == 4)
        assertTrue("CGs for set contains " + RR_PAIR_IN_CG + " in " + assetOptions, optionsContainValue(assetOptions,RR_PAIR_IN_CG))
        assertTrue("CGs for set contains " + RR_PAIR_IN_CG_IN_RR_GRP + " in " + assetOptions, optionsContainValue(assetOptions,RR_PAIR_IN_CG_IN_RR_GRP))
        assertTrue("CGs for set contains " + RR_PAIR_IN_RR_GRP + " in " + assetOptions, optionsContainValue(assetOptions,RR_PAIR_IN_RR_GRP))
        assertTrue("CGs for set contains " + RR_PAIR_IN_RR_SET + " in " + assetOptions, optionsContainValue(assetOptions,RR_PAIR_IN_RR_SET))

        // save IDs to run services later
        RR_PAIR_IN_CG_ID = optionKeyForValue(assetOptions,RR_PAIR_IN_CG)
        RR_PAIR_IN_CG_IN_RR_GRP_ID = optionKeyForValue(assetOptions,RR_PAIR_IN_CG_IN_RR_GRP)
        RR_PAIR_IN_RR_GRP_ID = optionKeyForValue(assetOptions,RR_PAIR_IN_RR_GRP)
        RR_PAIR_IN_RR_SET_ID = optionKeyForValue(assetOptions,RR_PAIR_IN_RR_SET)
    }

    static void pairsForGrpTest(){
        println "Testing Asset Option Provider for RR Pairs in RR Grp"
        def params = [(AO_RR_SETS_FOR_TYPE):RR_SET_ID,(AO_RR_GROUPS_FOR_SET):RR_GROUP_ID,(AO_RR_CG_OR_PAIR):CG_OR_PAIR_PAIR]
        List<AssetOption> assetOptions = getOptions(AO_RR_PAIRS_OR_CGS,params)
        assertTrue("One asset options returned for " + AO_RR_PAIRS_OR_CGS, assetOptions.size() == 1)
        assertTrue("CGs for set contains " + RR_PAIR_IN_RR_GRP + " in " + assetOptions, optionsContainValue(assetOptions,RR_PAIR_IN_RR_GRP))
    }

    static void linkOpersTest(){

        def overrideParametersPairsInSet = [:]
        overrideParametersPairsInSet.remoteReplicationSet = RR_SET_ID
        overrideParametersPairsInSet.remoteReplicationGroup = RR_GROUP_NONE_OPTION
        overrideParametersPairsInSet.remoteReplicationCgOrPair = CG_OR_PAIR_PAIR
        overrideParametersPairsInSet.remoteReplicationPairsOrCGs = RR_PAIR_IN_RR_SET_ID

        def overrideParametersCGsInSet = [:]
        overrideParametersCGsInSet.remoteReplicationSet = RR_SET_ID
        overrideParametersCGsInSet.remoteReplicationGroup = RR_GROUP_NONE_OPTION
        overrideParametersCGsInSet.remoteReplicationCgOrPair = CG_OR_PAIR_CG
        overrideParametersCGsInSet.remoteReplicationPairsOrCGs = CG_FOR_SET_ID

        // fix this ir change to test link op on entire group
        def overrideParametersPairsInGrp = [:]
        overrideParametersPairsInGrp.remoteReplicationSet = RR_SET_ID
        overrideParametersPairsInGrp.remoteReplicationGroup = RR_GROUP_ID
        overrideParametersPairsInGrp.remoteReplicationCgOrPair = CG_OR_PAIR_PAIR
        overrideParametersPairsInGrp.remoteReplicationPairsOrCGs = RR_PAIR_IN_RR_GRP_ID

        // fix this ir change to test link op on entire set
        def overrideParametersCGsInGrp = [:]
        overrideParametersCGsInGrp.remoteReplicationSet = RR_SET_ID
        overrideParametersCGsInGrp.remoteReplicationGroup = RR_GROUP2_ID
        overrideParametersCGsInGrp.remoteReplicationCgOrPair = CG_OR_PAIR_CG
        overrideParametersCGsInGrp.remoteReplicationPairsOrCGs = CG_FOR_GRP_ID

        def overrideParametersMap = [
            "Pair in set":overrideParametersPairsInSet,
            "CG in set":overrideParametersCGsInSet,
            //"Whole group":overrideParametersPairsInGrp,
            //"Whole Set":overrideParametersCGsInGrp
        ]

        println "TODO: add tests for operations on RR Pairs in an RR Group"
        println "TODO: add tests for operations on RR Pairs in a ConsistencyGroup"

        def servicePaths = [ // order of tests should insure operations are undone in next test
            "Failover":"BlockProtectionServices/RemoteReplicationManagement/FailoverRemoteReplicationPair",
            "Failback":"BlockProtectionServices/RemoteReplicationManagement/FailbackRemoteReplicationPair",
            "Split":"BlockProtectionServices/RemoteReplicationManagement/SplitRemoteReplicationPair",
            "Establish":"BlockProtectionServices/RemoteReplicationManagement/EstablishRemoteReplicationPair",
            "Suspend":"BlockProtectionServices/RemoteReplicationManagement/SuspendRemoteReplicationPair",
            "Resume":"BlockProtectionServices/RemoteReplicationManagement/ResumeRemoteReplicationPair",
            "Swap":"BlockProtectionServices/RemoteReplicationManagement/SwapRemoteReplicationPair",
            "Swap":"BlockProtectionServices/RemoteReplicationManagement/SwapRemoteReplicationPair",
            "Stop":"BlockProtectionServices/RemoteReplicationManagement/StopRemoteReplicationPair"
            //,"Move":"BlockProtectionServices/RemoteReplicationManagement/MoveRemoteReplicationPair"
        ]

        println "TODO: add test for Move operation"

        // execute all services for all param combos
        for (params in overrideParametersMap) {
            printInfo "Running tests for: " + params.key
            for (servicePath in servicePaths) {
                printInfo "  Testing catalog service: " + servicePath.key
                printVerbose servicePath.value + " : " + formatMap(params.value)
                placeOrder(servicePath.value, params.value)
                printVerbose "Service ran successfully"
            }
        }
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

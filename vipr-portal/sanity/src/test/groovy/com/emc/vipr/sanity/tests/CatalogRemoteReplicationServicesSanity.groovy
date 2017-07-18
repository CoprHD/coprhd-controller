/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.tests

import org.junit.AfterClass
import org.junit.Test

import static com.emc.vipr.sanity.catalog.RemoteReplicationHelper.*
import static com.emc.vipr.sanity.setup.RemoteReplicationSetup.*
import static com.emc.vipr.sanity.Sanity.*
import static com.emc.vipr.sanity.Sanity.printDebug
import static com.emc.vipr.sanity.Sanity.printVerbose
import static com.emc.vipr.sanity.Sanity.printInfo
import static com.emc.vipr.sanity.Sanity.printWarn
import static com.emc.vipr.sanity.Sanity.printError

public class CatalogRemoteReplicationServicesSanity {

    @Test void assetOptionOrderedTests() {
        printInfo "Starting tests..."

        storageTypeAssetOptionTest()
        rrSetsAssetOptionTest()
        rrGroupsForSetAssetOptionTest()
        cgOrPairOptionTest()
        cgsOrPairsDependenciesTest()
        cgsForGrpTest()
        cgsForSetTest()
        pairsForSetTest()
        pairsForGrpTest()

        printInfo "Tests Complete"
    }

    @AfterClass static void cleanup() {
        if(!removeTopologyWhenDone) {
            printInfo("Topology being left in place")
            return
        }

        printInfo "Cleaning up..."
        clearTopology()
        printInfo "Cleanup complete"
    }
}

/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.tests

import org.junit.AfterClass
import org.junit.Test

import com.emc.vipr.sanity.catalog.RemoteReplicationHelper
import com.emc.vipr.sanity.setup.RemoteReplicationSetup
import static com.emc.vipr.sanity.Sanity.printDebug
import static com.emc.vipr.sanity.Sanity.printVerbose
import static com.emc.vipr.sanity.Sanity.printInfo
import static com.emc.vipr.sanity.Sanity.printWarn
import static com.emc.vipr.sanity.Sanity.printError

public class CatalogRemoteReplicationServicesSanity {

    @Test void assetOptionOrderedTests() {
        printInfo "Starting tests..."
        RemoteReplicationHelper.storageTypeAssetOptionTest()
        RemoteReplicationHelper.rrSetsAssetOptionTest()
        RemoteReplicationHelper.rrGroupsForSetAssetOptionTest()
        printInfo "Tests Complete"
    }

    @AfterClass static void cleanup() {
        printInfo "Cleaning up..."
        RemoteReplicationSetup.deleteRrVolume(RemoteReplicationSetup.VOL_IN_SET_NAME)
        RemoteReplicationSetup.deleteRrVolume(RemoteReplicationSetup.VOL_IN_GRP_NAME)
        RemoteReplicationSetup.deleteRrVolume(RemoteReplicationSetup.VOL_IN_CG_NAME)
        RemoteReplicationSetup.deleteRrVolume(RemoteReplicationSetup.VOL_IN_CG_IN_GRP_NAME)
        RemoteReplicationSetup.deleteCg(RemoteReplicationSetup.CG_NAME_FOR_GRP)
        RemoteReplicationSetup.deleteCg(RemoteReplicationSetup.CG_NAME_FOR_SET)
        printInfo "Cleanup complete"
    }
}

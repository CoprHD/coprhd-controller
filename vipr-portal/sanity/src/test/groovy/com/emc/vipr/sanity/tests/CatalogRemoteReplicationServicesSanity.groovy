/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.tests

import org.junit.AfterClass
import org.junit.Test

import com.emc.vipr.sanity.catalog.RemoteReplicationHelper
import com.emc.vipr.sanity.setup.RemoteReplicationSetup

public class CatalogRemoteReplicationServicesSanity {

    @Test void assetOptionOrderedTests() {
        RemoteReplicationHelper.storageTypeAssetOptionTest()
        RemoteReplicationHelper.rrSetsAssetOptionTest()
        RemoteReplicationHelper.rrGroupsForSetAssetOptionTest()
    }

    @AfterClass static void cleanup() {
        println "cleaning up..."
        RemoteReplicationSetup.deleteRrVolume(RemoteReplicationSetup.VOL_IN_SET_NAME)
        RemoteReplicationSetup.deleteRrVolume(RemoteReplicationSetup.VOL_IN_GRP_NAME)
        RemoteReplicationSetup.deleteRrVolume(RemoteReplicationSetup.VOL_IN_CG_NAME)
        RemoteReplicationSetup.deleteRrVolume(RemoteReplicationSetup.VOL_IN_CG_IN_GRP_NAME)
        RemoteReplicationSetup.deleteCg(RemoteReplicationSetup.CG_NAME)
        println "cleanup complete"
    }
}

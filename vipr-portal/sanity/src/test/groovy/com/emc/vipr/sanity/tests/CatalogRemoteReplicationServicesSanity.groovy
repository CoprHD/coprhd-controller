/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.tests

import org.junit.AfterClass
import org.junit.Test

import com.emc.vipr.sanity.catalog.RemoteReplicationHelper

public class CatalogRemoteReplicationServicesSanity {


    @Test void assetOptionOrderedTests() {
        RemoteReplicationHelper.storageTypeAssetOptionTest()
        RemoteReplicationHelper.rrSetsAssetOptionTest()
        RemoteReplicationHelper.rrGroupsForSetAssetOptionTest()
        RemoteReplicationHelper.createRrVolumes()
    }

    @AfterClass static void cleanup() {
        println "CatalogRemoteReplicationServicesSanity.cleanup complete (nothing to do)"
    }
}

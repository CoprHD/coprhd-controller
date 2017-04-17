/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.catalog

import static com.emc.vipr.sanity.Sanity.*
import static org.junit.Assert.*

class BlockServicesHelper {

    static def CREATE_BLOCK_VOLUME_SERVICE = "BlockStorageServices/CreateBlockVolume"
    static def CREATE_BLOCK_SNAPSHOT_SERVICE = "BlockProtectionServices/CreateBlockSnapshot"
    static def REMOVE_BLOCK_VOLUME_SERVICE = "BlockStorageServices/RemoveBlockVolumes"
    static def REMOVE_BLOCK_SNAPSHOT_SERVICE = "BlockProtectionServices/RemoveBlockSnapshot"
    static def CREATE_BLOCK_FULLCOPY_SERVICE = "BlockProtectionServices/CreateFullCopy"
    static def REMOVE_BLOCK_FULLCOPY_SERVICE = "BlockProtectionServices/RemoveFullCopies"
    static def CREATE_BLOCK_VOLUME_FORHOST_SERVICE = "BlockStorageServices/CreateBlockVolumeForAHost"
    static def UNEXPORT_AND_REMOVE_BLOCK_VOLUMES = "BlockStorageServices/UnexportAndRemoveBlockVolumes"

    static def CREATE_VOLUME_AND_DATASTORE_SERVICE = "BlockServicesforVMwarevCenter/CreateVolumeandDatastore"

    static void createVolumeAndDatastoreTest() {
        println " ## Create Volume and Datastore Test ## "

        def creationOrder = createVolumeAndDatastore()

        //    deleteDatastoreAndVolume(creationOrder)
    }

    static void createAndRemoveBlockVolumeForHostTest() {
        println "  ## Create Block Volume for Host Test ## "

        // place the order to create a block volume
        def creationOrder = createBlockVolumeForHost()


        // place the order to remove the block volume from the creation order
        removeBlockVolumeForHost(creationOrder)
    }

    static void createFullCopyAndRemoveBlockVolumeTest() {
        println "  ## Create Block Full Copy Volume Test ## "

        // place the order to create a block volume
        def creationOrder = createBlockVolume()

        // create snapshot
        def fullCopyOrder = createBlockFullCopy()
        removeBlockFullCopy(fullCopyOrder)

        // place the order to remove the block volume from the creation order
        removeBlockVolume(creationOrder)

    }

    static void createSnapshotAndRemoveBlockVolumeTest() {
        println "  ## Create Block Snapshot Volume Test ## "

        // place the order to create a block volume
        def creationOrder = createBlockVolume()

        // create snapshot
        def snapshotOrder = createBlockSnapshot()
        removeBlockSnapshot(snapshotOrder, "local")
        removeBlockSnapshot(snapshotOrder, "session")

        // place the order to remove the block volume from the creation order
        removeBlockVolume(creationOrder)
    }

    static void createAndRemoveBlockVolumeTest() {
        println "  ## Create Block Volume Test ## "

        // place the order to create a block volume
        def creationOrder = createBlockVolume()

        // place the order to remove the block volume from the creation order
        removeBlockVolume(creationOrder)
    }

    static def createBlockVolume() {
        def overrideParameters = [:]
        overrideParameters.name = "create_block_volume_test_"+Calendar.instance.time.time
        overrideParameters.size = "1"
        return CatalogServiceHelper.placeOrder(CREATE_BLOCK_VOLUME_SERVICE, overrideParameters)
    }

    static def createBlockVolumeForHost() {
        def overrideParameters = [:]
        overrideParameters.name = "create_block_volume_test_"+Calendar.instance.time.time
        overrideParameters.size = "1"
        return CatalogServiceHelper.placeOrder(CREATE_BLOCK_VOLUME_FORHOST_SERVICE, overrideParameters)
    }

    static def createBlockFullCopy() {
        def overrideParameters = [:]
        overrideParameters.name = "create_block_fullcopy_test_"+Calendar.instance.time.time
        return CatalogServiceHelper.placeOrder(CREATE_BLOCK_FULLCOPY_SERVICE, overrideParameters)
    }

    static def createBlockSnapshot() {
        def overrideParameters = [:]
        overrideParameters.name = "create_block_snapshot_test_"+Calendar.instance.time.time
        return CatalogServiceHelper.placeOrder(CREATE_BLOCK_SNAPSHOT_SERVICE, overrideParameters)
    }

    static def getVolume(affectedResources) {
        for ( resource in affectedResources ) {
            if(resource.contains("Volume")) {
                return resource;
            }
        }
    }

    static def removeBlockVolumeForHost(creationOrder) {
        def overrideParameters = [:]
        def executionInfo = CatalogServiceHelper.getExecutionInfo(creationOrder)
        assertNotNull(executionInfo)
        assertNotNull(executionInfo.affectedResources)
        assertEquals(3, executionInfo.affectedResources.size())
        def createdVolumeId = getVolume(executionInfo.affectedResources)
        overrideParameters.volumes = createdVolumeId
        return CatalogServiceHelper.placeOrder(UNEXPORT_AND_REMOVE_BLOCK_VOLUMES, overrideParameters)
    }

    static def removeBlockFullCopy(creationOrder) {
        def overrideParameters = [:]
        def executionInfo = CatalogServiceHelper.getExecutionInfo(creationOrder)
        assertNotNull(executionInfo)
        assertNotNull(executionInfo.affectedResources)
        assertEquals(1, executionInfo.affectedResources.size())
        def createdVolumeId = executionInfo.affectedResources[0]
        overrideParameters.copies = createdVolumeId
        overrideParameters.type = "local"
        return CatalogServiceHelper.placeOrder(REMOVE_BLOCK_FULLCOPY_SERVICE, overrideParameters)
    }

    static def removeBlockSnapshot(creationOrder, type) {
        def overrideParameters = [:]
        def executionInfo = CatalogServiceHelper.getExecutionInfo(creationOrder)
        assertNotNull(executionInfo)
        assertNotNull(executionInfo.affectedResources)
        assertEquals(1, executionInfo.affectedResources.size())
        def createdVolumeId = executionInfo.affectedResources[0]
        if (type.equals("local")) { 
            overrideParameters.snapshots = createdVolumeId
        }
        overrideParameters.type = type 
        return CatalogServiceHelper.placeOrder(REMOVE_BLOCK_SNAPSHOT_SERVICE, overrideParameters)
    }

    static def removeBlockVolume(creationOrder) {
        def overrideParameters = [:]
        def executionInfo = CatalogServiceHelper.getExecutionInfo(creationOrder)
        assertNotNull(executionInfo)
        assertNotNull(executionInfo.affectedResources)
        assertEquals(1, executionInfo.affectedResources.size())
        def createdVolumeId = executionInfo.affectedResources[0]
        overrideParameters.volumes = createdVolumeId
        return CatalogServiceHelper.placeOrder(REMOVE_BLOCK_VOLUME_SERVICE, overrideParameters)
    }

    static def createVolumeAndDatastore() {
        def overrideParameters = [:]
        overrideParameters.esxHost = "cluster-1"
        overrideParameters.name = "create_datastore_volume_test_"+Calendar.instance.time.time
        overrideParameters.size = "1"
        overrideParameters.datastoreName = "datastore_"+Calendar.instance.time.time
        return CatalogServiceHelper.placeOrder(CREATE_VOLUME_AND_DATASTORE_SERVICE, overrideParameters)
    }
}

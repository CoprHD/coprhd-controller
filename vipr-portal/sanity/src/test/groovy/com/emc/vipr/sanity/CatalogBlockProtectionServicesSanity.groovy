package com.emc.vipr.sanity

import org.junit.Test

import com.emc.vipr.sanity.catalog.BlockServicesHelper


public class CatalogBlockProtectionServicesSanity {

    @Test void createBlockSnapshotTest() {
        BlockServicesHelper.createSnapshotAndRemoveBlockVolumeTest()
    }

    @Test void createBlockFullCopyTest() {
        BlockServicesHelper.createFullCopyAndRemoveBlockVolumeTest()
    }
}

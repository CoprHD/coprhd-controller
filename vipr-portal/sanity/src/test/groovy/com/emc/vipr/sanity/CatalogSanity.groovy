package com.emc.vipr.sanity

import org.junit.BeforeClass
import org.junit.Test

import com.emc.vipr.sanity.catalog.BlockServicesHelper
import com.emc.vipr.sanity.setup.Sanity
import com.emc.vipr.sanity.setup.VNXSetup


public class CatalogSanity {
    @BeforeClass static void setup() {
        Sanity.setup()
        VNXSetup.setupSimulator()
        println "Setup Complete"
        println ""
    }

    @Test void createBlockVolumeTest() {
        BlockServicesHelper.createAndRemoveBlockVolumeTest()
    }

    @Test void createBlockVolumeForHostTest() {
        BlockServicesHelper.createAndRemoveBlockVolumeForHostTest()
    }

    @Test void createBlockSnapshotTest() {
        BlockServicesHelper.createSnapshotAndRemoveBlockVolumeTest()
    }

    @Test void createBlockFullCopyTest() {
        BlockServicesHelper.createFullCopyAndRemoveBlockVolumeTest()
    }
}

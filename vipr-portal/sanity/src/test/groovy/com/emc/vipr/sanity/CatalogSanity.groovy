package com.emc.vipr.sanity

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import com.emc.vipr.sanity.catalog.BlockServicesHelper
import com.emc.vipr.sanity.setup.Sanity
import com.emc.vipr.sanity.setup.VNXSetup


public class CatalogSanity {
    static services_run = 0;

    @BeforeClass static void setup() {
        Sanity.setup()
        VNXSetup.setupSimulator()
        println "Setup Complete"
        println ""
    }

    @AfterClass static void after() {
        println "Placed ${services_run} catalog orders"
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

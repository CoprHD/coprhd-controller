package com.emc.vipr.sanity

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import com.emc.vipr.sanity.catalog.BlockServicesHelper
import com.emc.vipr.sanity.setup.Sanity
import com.emc.vipr.sanity.setup.VNXSetup


public class CatalogBlockProtectionServicesSanity {

    @BeforeClass static void setup() {
        Sanity.setup()
        VNXSetup.setupSimulator()
        println "Setup Complete"
        println ""
    }

    @AfterClass static void after() {
        println "Placed ${Sanity.services_run} catalog orders"
    }

    @Test void createBlockSnapshotTest() {
        BlockServicesHelper.createSnapshotAndRemoveBlockVolumeTest()
    }

    @Test void createBlockFullCopyTest() {
        BlockServicesHelper.createFullCopyAndRemoveBlockVolumeTest()
    }
}

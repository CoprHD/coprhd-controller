package com.emc.vipr.sanity

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import com.emc.vipr.sanity.catalog.BlockServicesHelper
import com.emc.vipr.sanity.setup.Sanity
import com.emc.vipr.sanity.setup.VNXSetup
import com.emc.vipr.sanity.setup.VCenterSetup

public class CatalogVmwareBlockServicesSanity {

    @BeforeClass static void setup() {
        Sanity.setup()
        VNXSetup.setupSimulator()
        VCenterSetup.setup()
        println "Setup Complete"
        println ""
    }

    @AfterClass static void after() {
        println "Placed ${Sanity.services_run} catalog orders"
    }

    @Test void createVolumeAndDatastoreTest() {
       //BlockServicesHelper.createVolumeAndDatastoreTest()
    }
}

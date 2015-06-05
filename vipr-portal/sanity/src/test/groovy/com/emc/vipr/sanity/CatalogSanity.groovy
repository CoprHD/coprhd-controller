package com.emc.vipr.sanity

import com.emc.vipr.sanity.catalog.BlockServicesHelper;

import com.emc.vipr.sanity.setup.Sanity

import org.junit.Test
import org.junit.BeforeClass

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
}

package com.emc.vipr.sanity

import org.junit.Test

import com.emc.vipr.sanity.catalog.BlockServicesHelper

public class CatalogVmwareBlockServicesSanity {

    @Test void createVolumeAndDatastoreTest() {
        BlockServicesHelper.createVolumeAndDatastoreTest()
    }
}

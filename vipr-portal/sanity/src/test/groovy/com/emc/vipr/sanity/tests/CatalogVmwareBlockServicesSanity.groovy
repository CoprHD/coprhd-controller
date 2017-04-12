/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.tests

import org.junit.Test

import com.emc.vipr.sanity.catalog.BlockServicesHelper

public class CatalogVmwareBlockServicesSanity {

    @Test void createVolumeAndDatastoreTest() {
        BlockServicesHelper.createVolumeAndDatastoreTest()
    }
}

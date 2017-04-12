/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.tests

import org.junit.Test

import com.emc.vipr.sanity.catalog.BlockServicesHelper

public class CatalogBlockServicesSanity {

    @Test void createBlockVolumeTest() {
        BlockServicesHelper.createAndRemoveBlockVolumeTest()
    }

    @Test void createBlockVolumeForHostTest() {
        BlockServicesHelper.createAndRemoveBlockVolumeForHostTest()
    }
}

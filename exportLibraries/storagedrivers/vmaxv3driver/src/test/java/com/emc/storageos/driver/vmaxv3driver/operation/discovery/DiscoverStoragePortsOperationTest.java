/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.operation.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Test class for port discovery.
 *
 * Created by gang on 7/5/16.
 */
public class DiscoverStoragePortsOperationTest {

    private static Logger logger = LoggerFactory.getLogger(DiscoverStoragePortsOperationTest.class);

    @Test
    public void filterDirectorIds() {
        String[] directorIds = {
            "DF-1C",
            "DF-2C",
            "DF-3C",
            "DF-4C",
            "DX-3H",
            "DX-4H",
            "ED-1B",
            "ED-2B",
            "ED-3B",
            "ED-4B",
            "FA-1D",
            "FA-2D",
            "FA-3D",
            "FA-4D",
            "IM-1A",
            "IM-2A",
            "IM-3A",
            "IM-4A",
            "RF-1G",
            "RF-2G",
            "SE-1F",
            "SE-2F",
            "SE-3F",
            "SE-4F"
        };
        List<String> filteredDirectorIds = new DiscoverStoragePortsOperation().filterDirectorIds(
            Arrays.asList(directorIds));
        logger.debug("filteredDirectorIds = {}", filteredDirectorIds);
    }
}

/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * Test cases for utility methods.
 *
 * Created by gang on 7/6/16.
 */
public class DriverUtilTest {

    private static Logger logger = LoggerFactory.getLogger(DriverUtilTest.class);

    @Test
    public void testFormatWwn() {
        logger.debug("wwn = {}", DriverUtil.formatWwn("500009735014fc18"));
    }

}

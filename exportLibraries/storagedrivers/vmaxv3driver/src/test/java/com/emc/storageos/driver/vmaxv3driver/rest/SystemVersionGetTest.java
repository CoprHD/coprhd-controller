/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * Created by gang on 6/23/16.
 */
public class SystemVersionGetTest extends BaseRestTest {

    private static final Logger logger = LoggerFactory.getLogger(SystemVersionGetTest.class);

    @Test
    public void test() {
        logger.info("Version = {}", new SystemVersionGet().perform(this.getClient()));
    }
}

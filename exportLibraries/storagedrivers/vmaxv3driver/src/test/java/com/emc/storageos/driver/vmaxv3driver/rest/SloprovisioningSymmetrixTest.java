package com.emc.storageos.driver.vmaxv3driver.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * Created by gang on 6/23/16.
 */
public class SloprovisioningSymmetrixTest extends BaseRestTest {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixTest.class);

    @Test
    public void testList() {
        logger.info("Array list = {}", new SloprovisioningSymmetrixList().perform(this.getClient()));
    }

    @Test
    public void testGet() {
        logger.info("Symmetrix = {}", new SloprovisioningSymmetrixGet(
            this.getDefaultArray().getArrayId()).perform(this.getClient()));
    }
}

package com.emc.storageos.driver.vmaxv3driver.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * Created by gang on 6/23/16.
 */
public class SloprovisioningSymmetrixGetTest extends BaseRestTest {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixGetTest.class);

    @Test
    public void test() {
        logger.info("Symmetrix = {}", new SloprovisioningSymmetrixGet(this.getDefaultArrayId()).perform(this.getClient()));
    }
}

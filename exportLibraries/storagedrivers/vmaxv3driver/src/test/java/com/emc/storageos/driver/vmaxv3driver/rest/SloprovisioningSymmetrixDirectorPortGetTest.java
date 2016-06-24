package com.emc.storageos.driver.vmaxv3driver.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * Created by gang on 6/24/16.
 */
public class SloprovisioningSymmetrixDirectorPortGetTest extends BaseRestTest {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixDirectorPortGetTest.class);

    @Test
    public void test() {
        logger.info("SymmetrixPort = {}", new SloprovisioningSymmetrixDirectorPortGet(this.getDefaultArrayId(),
            this.getDefaultDirectorId(), this.getDefaultPortId()).perform(this.getClient()));
    }
}

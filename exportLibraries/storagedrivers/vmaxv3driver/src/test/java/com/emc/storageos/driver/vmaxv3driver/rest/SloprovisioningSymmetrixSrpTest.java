package com.emc.storageos.driver.vmaxv3driver.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * Created by gang on 6/23/16.
 */
public class SloprovisioningSymmetrixSrpTest extends BaseRestTest {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixSrpTest.class);

    @Test
    public void testList() {
        logger.info("SRP list = {}", new SloprovisioningSymmetrixSrpList(
            this.getDefaultArray().getArrayId()).perform(this.getClient()));
    }

    @Test
    public void testGet() {
        logger.info("Srp = {}", new SloprovisioningSymmetrixSrpGet(
            this.getDefaultArray().getArrayId(), this.getDefaultArray().getSrpId()).perform(this.getClient()));
    }
}

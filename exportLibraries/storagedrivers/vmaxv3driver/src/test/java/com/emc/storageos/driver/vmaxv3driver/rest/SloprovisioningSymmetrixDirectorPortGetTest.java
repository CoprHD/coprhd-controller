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
    public void testFcPort() {
        ArrayInfo array = this.getArrayWithFcPort();
        logger.info("Symmetrix FC Port = {}", new SloprovisioningSymmetrixDirectorPortGet(array.getArrayId(),
            array.getDirectorId(), array.getPortId()).perform(this.getClient()));
    }

    @Test
    public void testIscsiPort() {
        ArrayInfo array = this.getArrayWithIscsiPort();
        logger.info("Symmetrix iSCSI Port = {}", new SloprovisioningSymmetrixDirectorPortGet(array.getArrayId(),
            array.getDirectorId(), array.getPortId()).perform(this.getClient()));
    }

    @Test
    public void testRdfPort() {
        ArrayInfo array = this.getArrayWithRdfPort();
        logger.info("Symmetrix RDF Port = {}", new SloprovisioningSymmetrixDirectorPortGet(array.getArrayId(),
            array.getDirectorId(), array.getPortId()).perform(this.getClient()));
    }

    @Test
    public void testDiskPort() {
        logger.info("Symmetrix disk port = {}", new SloprovisioningSymmetrixDirectorPortGet(this.getDefaultArray().getArrayId(),
            this.getDefaultArray().getDirectorId(), this.getDefaultArray().getPortId()).perform(this.getClient()));
    }
}

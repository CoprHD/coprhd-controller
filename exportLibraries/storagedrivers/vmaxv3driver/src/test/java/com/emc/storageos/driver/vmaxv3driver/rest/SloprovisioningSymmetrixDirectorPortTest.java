package com.emc.storageos.driver.vmaxv3driver.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * Created by gang on 6/24/16.
 */
public class SloprovisioningSymmetrixDirectorPortTest extends BaseRestTest {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixDirectorPortTest.class);

    @Test
    public void testGetFcPort() {
        ArrayInfo array = this.getArrayWithFcPort();
        logger.info("Symmetrix FC Port = {}", new SloprovisioningSymmetrixDirectorPortGet(array.getArrayId(),
            array.getDirectorId(), array.getPortId()).perform(this.getClient()));
    }

    @Test
    public void testGetIscsiPort() {
        ArrayInfo array = this.getArrayWithIscsiPort();
        logger.info("Symmetrix iSCSI Port = {}", new SloprovisioningSymmetrixDirectorPortGet(array.getArrayId(),
            array.getDirectorId(), array.getPortId()).perform(this.getClient()));
    }

    @Test
    public void testGetRdfPort() {
        ArrayInfo array = this.getArrayWithRdfPort();
        logger.info("Symmetrix RDF Port = {}", new SloprovisioningSymmetrixDirectorPortGet(array.getArrayId(),
            array.getDirectorId(), array.getPortId()).perform(this.getClient()));
    }

    @Test
    public void testGetDiskPort() {
        logger.info("Symmetrix disk port = {}", new SloprovisioningSymmetrixDirectorPortGet(
            this.getDefaultArray().getArrayId(), this.getDefaultArray().getDirectorId(),
            this.getDefaultArray().getPortId()).perform(this.getClient()));
    }

    @Test
    public void testList() {
        logger.info("Port list = {}", new SloprovisioningSymmetrixDirectorPortList(this.getDefaultArray().getArrayId(),
            this.getDefaultArray().getDirectorId()).perform(this.getClient()));
    }
}

package com.emc.storageos.driver.vmaxv3driver.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * Created by gang on 9/27/16.
 */
public class SloprovisioningSymmetrixStorageGroupTest extends BaseRestTest {

    private static final Logger logger = LoggerFactory.getLogger(
        SloprovisioningSymmetrixStorageGroupTest.class);

    @Test
    public void testListByName() {
        logger.info("StorageGroup list = {}", new SloprovisioningSymmetrixStorageGroupListByName(
            this.getDefaultArray().getArrayId(), "test_vmaxv3").perform(this.getClient()));
    }

    @Test(dependsOnMethods = { "testListByName" })
    public void testCreate() {
        String body = "{\"srpId\": \"none\", \"storageGroupId\": \"" + this.getStorageGroupId() + "\", " +
            "\"sloBasedStorageGroupParam\": [{\"num_of_vols\": 2, \"volumeAttribute\": " +
            "{\"volume_size\": \"10\", \"capacityUnit\": \"GB\"}}]}";
        logger.info("body = {}", body);
        logger.info("StorageGroup creation result = {}", new SloprovisioningSymmetrixStorageGroupPost(
            this.getDefaultArray().getArrayId(), body).perform(this.getClient()));
    }

    @Test(dependsOnMethods = { "testCreate" })
    public void testUpdate() {
        String body = "{\"editStorageGroupActionParam\": {\"expandStorageGroupParam\": {" +
            "\"num_of_vols\": 1, \"volumeAttribute\": {\"volume_size\": \"10\", \"capacityUnit\": \"GB\"}, " +
            "\"create_new_volumes\": true}}}";
        logger.info("body = {}", body);
        logger.info("StorageGroup updating result = {}", new SloprovisioningSymmetrixStorageGroupPut(
            this.getDefaultArray().getArrayId(), this.getStorageGroupId(), body).perform(this.getClient()));
    }

    @Test(dependsOnMethods = { "testUpdate" })
    public void testDelete() {
        logger.info("StorageGroup deleting result = {}", new SloprovisioningSymmetrixStorageGroupDelete(
            this.getDefaultArray().getArrayId(), this.getStorageGroupId()).perform(this.getClient()));
    }
}

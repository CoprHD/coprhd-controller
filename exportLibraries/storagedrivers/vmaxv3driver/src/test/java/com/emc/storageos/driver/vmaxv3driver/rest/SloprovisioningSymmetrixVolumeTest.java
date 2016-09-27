package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.rest.response.Volume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gang on 9/27/16.
 */
public class SloprovisioningSymmetrixVolumeTest extends BaseRestTest {

    private static final Logger logger = LoggerFactory.getLogger(
        SloprovisioningSymmetrixStorageGroupTest.class);

    private List<String> volumeIds;

    @Test
    public void testCreate() {
        String body = "{\"srpId\": \"none\", \"storageGroupId\": \"" + this.getStorageGroupId() + "\", " +
            "\"sloBasedStorageGroupParam\": [{\"num_of_vols\": 2, \"volumeAttribute\": " +
            "{\"volume_size\": \"10\", \"capacityUnit\": \"GB\"}}]}";
        logger.info("body = {}", body);
        logger.info("StorageGroup creation result = {}", new SloprovisioningSymmetrixStorageGroupPost(
            this.getDefaultArray().getArrayId(), body).perform(this.getClient()));
    }

    @Test(dependsOnMethods = { "testCreate" })
    public void testListVolumes() {
        List<String> volumeIds = (List<String>)new SloprovisioningSymmetrixVolumeListByStorageGroup(
            this.getDefaultArray().getArrayId(), this.getStorageGroupId()).perform(this.getClient());
        this.volumeIds = volumeIds;
        logger.info("Volumes listing result = {}", volumeIds);
    }

    @Test(dependsOnMethods = { "testListVolumes" })
    public void testGetVolumes() {
        for (String volumeId : this.volumeIds) {
            Volume volume = (Volume)new SloprovisioningSymmetrixVolumeGet(
                this.getDefaultArray().getArrayId(), volumeId).perform(this.getClient());
            logger.info("Volume = {}", volume);
        }
    }

    @Test(dependsOnMethods = { "testGetVolumes" })
    public void testDeleteStorageGroup() {
        logger.info("StorageGroup deleting result = {}", new SloprovisioningSymmetrixStorageGroupDelete(
            this.getDefaultArray().getArrayId(), this.getStorageGroupId()).perform(this.getClient()));
    }

    @Test(dependsOnMethods = { "testDeleteStorageGroup" })
    public void testDeleteVolumes() {
        for (String volumeId : this.volumeIds) {
            logger.info("Volume '{}' deleting result = {}", volumeId, new SloprovisioningSymmetrixVolumeDelete(
                this.getDefaultArray().getArrayId(), volumeId).perform(this.getClient()));
        }
    }

    @Test //(dependsOnMethods = { "testDeleteVolumes" })
    public void testGetVolumesAgain() {
        this.volumeIds = new ArrayList<String>();
        volumeIds.add("000CD");
        volumeIds.add("00544");
        for (String volumeId : this.volumeIds) {
            Volume volume = (Volume)new SloprovisioningSymmetrixVolumeGet(
                this.getDefaultArray().getArrayId(), volumeId).perform(this.getClient());
            logger.info("Volume = {}", volume);
        }
    }
}

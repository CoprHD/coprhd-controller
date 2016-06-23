/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver;

import com.emc.storageos.storagedriver.model.StorageBlockObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * Testing class for "Vmaxv3StorageDriver".
 *
 * Created by gang on 6/21/16.
 */
public class Vmaxv3StorageDriverTest {

    private static Logger logger = LoggerFactory.getLogger(Vmaxv3StorageDriverTest.class);

    Vmaxv3StorageDriver driver = new Vmaxv3StorageDriver();

    @Test
    public void testGetRegistrationData() {
        logger.info("getRegistrationData = {}", driver.getRegistrationData());
    }

    @Test
    public void testGetTask() {
        logger.info("getTask = {}", driver.getTask(null));
    }

    @Test
    public void testGetStorageObject() {
        logger.info("getStorageObject = {}", driver.getStorageObject(null, null, StorageBlockObject.class));
    }
}

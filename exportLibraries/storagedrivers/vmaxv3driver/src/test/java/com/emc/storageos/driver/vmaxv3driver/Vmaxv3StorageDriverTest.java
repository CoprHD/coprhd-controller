package com.emc.storageos.driver.vmaxv3driver;

import com.emc.storageos.storagedriver.model.StorageBlockObject;
import org.testng.annotations.Test;

public class Vmaxv3StorageDriverTest {

    Vmaxv3StorageDriver driver = new Vmaxv3StorageDriver();

    @Test
    public void testGetRegistrationData() {
        System.out.println(driver.getRegistrationData());
    }

    @Test
    public void testGetTask() {
        System.out.println(driver.getTask(null));
    }

    @Test
    public void testGetStorageObject() {
        System.out.println(driver.getStorageObject(null, null, StorageBlockObject.class));
    }
}

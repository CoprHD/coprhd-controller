/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package util;

import static util.BourneUtil.getViprClient;

import java.io.File;

import com.emc.storageos.model.storagedriver.StorageDriverList;
import com.sun.jersey.api.client.ClientResponse;

public class StorageDriverUtils {

    public static StorageDriverList getDrivers() {
        return getViprClient().storageDriver().getDrivers();
    }

    public static ClientResponse installDriver(File f) {
        return getViprClient().storageDriver().installDriver(f);
    }

    public static ClientResponse upgradeDriver(File driverFile, String driverName) {
        return getViprClient().storageDriver().upgradeDriver(driverName, driverFile);
    }

    public static ClientResponse uninstallDriver(String driverName) {
        return getViprClient().storageDriver().uninstallDriver(driverName);
    }
}

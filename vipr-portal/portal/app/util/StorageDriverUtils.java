/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package util;

import static util.BourneUtil.getViprClient;

import java.io.File;

import com.emc.storageos.model.storagedriver.StorageDriverList;
import com.emc.storageos.model.storagedriver.StorageDriverRestRep;
import com.sun.jersey.api.client.ClientResponse;

public final class StorageDriverUtils {

    private StorageDriverUtils() {}

    public static StorageDriverRestRep getDriver(String name) {
        try {
            return getViprClient().storageDriver().getDriver(name);
        } catch (Exception e) {
            return null;
        }
    }

    public static StorageDriverList getDrivers() {
        return getViprClient().storageDriver().getDrivers();
    }

    public static ClientResponse installDriver(File f) {
        return getViprClient().storageDriver().installDriver(f);
    }

    public static ClientResponse upgradeDriver(File driverFile, String driverName, boolean force) {
        return getViprClient().storageDriver().upgradeDriver(driverName, driverFile, force);
    }

    public static ClientResponse uninstallDriver(String driverName) {
        return getViprClient().storageDriver().uninstallDriver(driverName);
    }
}

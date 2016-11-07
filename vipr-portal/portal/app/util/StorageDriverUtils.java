/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package util;

import static util.BourneUtil.getViprClient;

import com.emc.storageos.model.storagedriver.StorageDriverList;

public class StorageDriverUtils {

    public static StorageDriverList getDrivers() {
        return getViprClient().storageDriver().getDrivers();
    }
}

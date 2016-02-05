/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migrated StorageSystem serialNumber from ARRAY.R700.94677 to 94677.
 * 
 * 
 */
public class HDSStorageSystemSerialNumberMigration extends
        BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory.getLogger(HDSStorageSystemSerialNumberMigration.class);
    private static String ARRAY = "ARRAY";
    private static String DOT_OPERATOR = "\\.";

    /**
     * Process the HDS storagesystems and update their serialNumber from ARRAY.R700.94677 to 94677.
     */
    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();

        try {
            List<URI> storageSystemURIList = dbClient.queryByType(StorageSystem.class, true);
            List<StorageSystem> storageSystemsList = dbClient.queryObject(StorageSystem.class, storageSystemURIList);
            Iterator<StorageSystem> systemItr = storageSystemsList.iterator();
            List<StorageSystem> systemsToUpdate = new ArrayList<StorageSystem>();
            while (systemItr.hasNext()) {
                StorageSystem storageSystem = systemItr.next();
                // perform storagesystem upgrade only fro HDS storagesystems.
                if (DiscoveredDataObject.Type.hds.name().equalsIgnoreCase(storageSystem.getSystemType())) {
                    String serialNumber = storageSystem.getSerialNumber();
                    if (serialNumber.contains(ARRAY)) {
                        String[] dotSeperatedStrings = serialNumber.split(DOT_OPERATOR);
                        String serialNumberToUpdate = dotSeperatedStrings[dotSeperatedStrings.length - 1];
                        storageSystem.setSerialNumber(serialNumberToUpdate);
                        systemsToUpdate.add(storageSystem);
                    }
                }
            }
            // persist all systems here.
            dbClient.persistObject(systemsToUpdate);
        } catch (Exception e) {
            log.error("Exception occured while updating hds storagesystem serialnumber");
            log.error(e.getMessage(), e);
        }
    }

}

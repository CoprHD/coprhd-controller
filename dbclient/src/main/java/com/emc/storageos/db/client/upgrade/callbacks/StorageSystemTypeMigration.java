package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class StorageSystemTypeMigration extends BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory.getLogger(StorageSystemTypeMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();
        for (URI typeUri : dbClient.queryByType(StorageSystemType.class, true)) {
            StorageSystemType type = dbClient.queryObject(StorageSystemType.class, typeUri);
            // TODO Need to confirm with Evgeny about the logic to init supportedStorageProfiles field
        }
        log.info("StorageSystemTypeMigration has been successfully completed");
    }
}

/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import static com.emc.storageos.db.client.model.util.StorageSystemTypeUtils.getSupportedStorageProfiles;

import java.net.URI;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.db.client.model.StorageSystemType.META_TYPE;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * New field supportedStorageProfiles is added for StorageSystemType model after 3.6, so when upgrading
 * to a version that is after 3.6, this field of existing storage system types should be filled accordingly
 * 
 * Filling rules please see
 * {@link com.emc.storageos.db.client.model.util.StorageSystemTypeUtils#getSupportedStorageProfiles}
 */
public class StorageSystemTypeMigration extends BaseCustomMigrationCallback {

    private static final String DELLSCSYSTEM = "dellscsystem";
    private static final String DELLSCPROVIDER = "dellscprovider";

    private static final Logger log = LoggerFactory.getLogger(StorageSystemTypeMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();
        for (URI typeUri : dbClient.queryByType(StorageSystemType.class, true)) {
            StorageSystemType type = dbClient.queryObject(StorageSystemType.class, typeUri);
            String typeName = type.getStorageTypeName();
            // Delete dell sc storage and provider types, controller service will insert these types
            // when starting
            if (StringUtils.equals(typeName, DELLSCSYSTEM)
                    || StringUtils.equals(typeName, DELLSCPROVIDER)) {
                dbClient.removeObject(type);
                log.info("Removed StorageSystemType: {}", typeName);
                continue;
            }
            if (type.getIsNative() != null && !type.getIsNative()) {
                // CoprHD does not know what storage profiles a out-of-tree driver supported
                // storage system type supports, so leave it as it is without any processing.
                log.info("Bypassed StorageSystemType: {}", typeName);
                continue;
            }
            META_TYPE metaType = Enum.valueOf(META_TYPE.class, type.getMetaType().toUpperCase());
            StringSet profiles = getSupportedStorageProfiles(typeName, metaType);
            type.setSupportedStorageProfiles(profiles);
            dbClient.updateObject(type);
            log.info("Set StorageSystemType {}'s supportedStorageProfiles field to {}", typeName, profiles);
        }
        log.info("All storage system types' supportedStorageProfiles fields have been updated");
    }
}

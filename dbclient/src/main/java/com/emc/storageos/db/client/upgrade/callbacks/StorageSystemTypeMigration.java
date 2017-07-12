/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.db.client.model.StorageSystemType.META_TYPE;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.server.impl.StorageSystemTypesInitUtils;
import com.emc.storageos.storagedriver.storagecapabilities.StorageProfile;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * A new field called supportedStorageProfiles is added for StorageSystemType model after 3.6,
 * so when upgrading to a version that is after 3.6, this field of existing storage system types
 * should be filled accordingly.
 *
 * Filling Rules:
 * - For block and block provider's types, add BLOCK to supportedStorageProfiles field.
 * - For file and file provider's types, add FILE to supportedStorageProfiles field;
 * - Especially for VMAX type, add REMOTE_REPLICATION_FOR_BLOCK to supportedStorageProfiles field.
 */
public class StorageSystemTypeMigration extends BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory.getLogger(StorageSystemTypeMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();
        for (URI typeUri : dbClient.queryByType(StorageSystemType.class, true)) {
            StorageSystemType type = dbClient.queryObject(StorageSystemType.class, typeUri);
            if (type.getIsNative() != null && !type.getIsNative()) {
                continue;
            }
            String metaType = type.getMetaType();
            StringSet profiles = new StringSet();
            if (StringUtils.equals(metaType, META_TYPE.BLOCK.toString())
                    || StringUtils.equals(metaType, META_TYPE.BLOCK_PROVIDER.toString())) {
                profiles.add(StorageProfile.BLOCK.toString());
            } else if (StringUtils.equals(metaType, META_TYPE.FILE.toString())
                    || StringUtils.equals(metaType, META_TYPE.FILE_PROVIDER.toString())) {
                profiles.add(StorageProfile.FILE.toString());
            }
            String typeName = type.getStorageTypeName();
            if (StringUtils.equals(typeName, StorageSystemTypesInitUtils.VMAX)) {
                profiles.add(StorageProfile.REMOTE_REPLICATION_FOR_BLOCK.toString());
            }
            type.setSupportedStorageProfiles(profiles);
            dbClient.updateObject(type);
            log.info("Set StorageSystemType {}'s supportedStorageProfiles field to {}", typeName, profiles);
        }
        log.info("All storage system types' supportedStorageProfiles fields have been updated");
    }
}

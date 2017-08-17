/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.util;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StorageSystemType.META_TYPE;
import com.emc.storageos.db.client.model.StorageSystemType.StorageProfile;
import com.emc.storageos.db.client.model.StringSet;

public final class StorageSystemTypeUtils {

    private static final Logger log = LoggerFactory.getLogger(StorageSystemTypeUtils.class);
    private static final String VMAX = "vmax";

    private StorageSystemTypeUtils() {
        
    }

    /**
     * Filling Rules:
     * - For block and block provider's types, add BLOCK to supportedStorageProfiles field.
     * - For file and file provider's types, add FILE to supportedStorageProfiles field;
     * - Especially for VMAX type, add REMOTE_REPLICATION_FOR_BLOCK to supportedStorageProfiles field.
     */
    public static StringSet getSupportedStorageProfiles(String typeName, META_TYPE metaType) {
        Set<String> profiles = new StringSet();
        switch (metaType) {
            case BLOCK:
            case BLOCK_PROVIDER:
                profiles.add(StorageProfile.BLOCK.toString());
                break;
            case FILE:
            case FILE_PROVIDER:
                profiles.add(StorageProfile.FILE.toString());
                break;
            case BLOCK_AND_FILE:
                profiles.add(StorageProfile.BLOCK.toString());
                profiles.add(StorageProfile.FILE.toString());
                break;
            case OBJECT:
                // TODO
                break;
            case ALL:
                // TODO
                break;
            default:
                log.error("Unrecognized meta type: {}", metaType);
        }
        if (VMAX.equals(typeName)) {
            profiles.add(StorageProfile.REMOTE_REPLICATION_FOR_BLOCK.toString());
        }
        return new StringSet(profiles);
    }
}

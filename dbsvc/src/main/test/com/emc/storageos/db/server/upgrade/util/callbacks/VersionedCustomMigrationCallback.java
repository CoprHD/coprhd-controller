/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.util.callbacks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class VersionedCustomMigrationCallback extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(VersionedCustomMigrationCallback.class);
    private boolean processed;

    @Override
    public void process() throws MigrationCallbackException {
        log.info("Processing version-specific custom migration callback: {}", getName());
        processed = true;
    }

    public boolean isProcessed() {
        return processed;
    }
}

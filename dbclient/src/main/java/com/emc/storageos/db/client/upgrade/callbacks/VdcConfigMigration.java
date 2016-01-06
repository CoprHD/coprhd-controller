/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

/**
 * Cleanup db config from pre-yoda release
 */
public class VdcConfigMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(
            VdcConfigMigration.class);

    @Override
    public void process() {
        coordinatorClient.deletePath(String.format("%s/%s", ZkPath.CONFIG, Constants.DB_CONFIG));
        coordinatorClient.deletePath(String.format("%s/%s", ZkPath.CONFIG, Constants.GEODB_CONFIG));
        log.info("Remove dbconfig/geodbconfig in zk global area successfully");
    }
}

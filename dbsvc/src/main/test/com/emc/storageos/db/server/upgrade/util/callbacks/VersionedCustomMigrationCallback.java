/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.server.upgrade.util.callbacks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

public class VersionedCustomMigrationCallback extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(VersionedCustomMigrationCallback.class);
    private boolean processed;

    @Override
    public void process() {
        log.info("Processing version-specific custom migration callback: {}", getName());
        processed = true;
    }

    public boolean isProcessed() {
        return processed;
    }
}

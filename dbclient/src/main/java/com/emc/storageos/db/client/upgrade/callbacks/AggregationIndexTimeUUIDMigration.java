/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DbIndex;
import com.emc.storageos.db.client.impl.AggregateDbIndex;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.services.util.AlertsLogger;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.HashMap;

/**
 * Migration for the Aggregation Index fields.
 * They are not going to use TimeUUID to enforce consistency.
 * Consistency is not enforced anyway. But UUID causes extraneous tombstones in the database.
 */
public class AggregationIndexTimeUUIDMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(AggregationIndexTimeUUIDMigration.class);
    private AlertsLogger alertLog = AlertsLogger.getAlertsLogger();

    @Override
    public void process() throws MigrationCallbackException {
        log.info("Starting aggregation index processing");
        boolean success = true;
        try {
            removeOldAggregationIndex();
            log.info("Removed old aggregation index");
            removeTimeUUIDIndexedFields(Volume.class);
        } catch (Exception e) {
            success = false;
        }

        try {
            log.info("Rebuilt aggregated fields for CF Volume");
            removeTimeUUIDIndexedFields(FileShare.class);
        } catch (Exception e) {
            success = false;
        }

        try {
            log.info("Rebuilt aggregated fields for CF FileShare");
            removeTimeUUIDIndexedFields(StoragePool.class);
            log.info("Rebuilt aggregated fields for CF StoragePool");
        } catch (Exception e) {
            success = false;
        }

        if (success == false) {
            String errMsg = String.format("The DB migration callback %s is completed, but leaves some corrupted rows unprocessed, " +
                    "See the dbsvc.log/geodbsvc.log for more detailed information. Please open an EMC support request for this issue",
                    this.getClass().getSimpleName());
            alertLog.error(errMsg);
        }
    }

    private void removeOldAggregationIndex() {
        InternalDbClient internalDbClient = getInternalDbClient();
        internalDbClient.rebuildCf("AggregatedIndex");
    }

    private void removeTimeUUIDIndexedFields(Class<? extends DataObject> clazz) throws Exception {
        DataObjectType doType = TypeMap.getDoType(clazz);
        Collection<ColumnField> fields = doType.getColumnFields();
        Map<String, ColumnField> uuidFields = new HashMap<>();
        for (ColumnField field : fields) {
            DbIndex index = field.getIndex();
            if (index != null && index instanceof AggregateDbIndex) {
                uuidFields.put(field.getName(), field);
            }
        }
        getInternalDbClient().resetFields(clazz, uuidFields, true); // true: ignore exception while accessing db
    }

    private InternalDbClient getInternalDbClient() {
        if (InternalDbClient.class.isAssignableFrom(dbClient.getClass())) {
            return (InternalDbClient) dbClient;
        } else {
            throw new IllegalStateException("Migration callback " + name + " needs InternalDbClient");
        }
    }
}

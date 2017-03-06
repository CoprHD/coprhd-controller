/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import com.google.common.collect.Sets;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.query.ColumnFamilyQuery;
import com.netflix.astyanax.util.TimeUUIDUtils;

/**
 * This migration handler is to fix issue COP-26680. 
 * Check CF FileShare and Snapshot to detect whether it contain duplicated index mentioned in issue COP-26680
 *
 */
public class RebuildIndexDuplicatedCFNameMigration extends BaseCustomMigrationCallback {
    

    private static final Logger log = LoggerFactory.getLogger(RebuildIndexDuplicatedCFNameMigration.class);
    private static final int REBUILD_INDEX_BATCH_SIZE = 1000;
    private Set<Class<? extends DataObject>> scanClasses = Sets.newHashSet(FileShare.class, Snapshot.class);
    private Set<String> fieldNames = Sets.newHashSet("path", "mountPath");
    private AtomicInteger totalProcessedIndex = new AtomicInteger(0);
    private BlockingQueue<Runnable> blockingQueue = new ArrayBlockingQueue<Runnable>(20);
    private ThreadPoolExecutor executor = new ThreadPoolExecutor(5, 10, 50, TimeUnit.MILLISECONDS, blockingQueue);
    
    @Override
    public void process() throws MigrationCallbackException {
        log.info("Begin to run migration handler RebuildIndexDuplicatedCFName");
        long beginTime = System.currentTimeMillis();
        
        for (Class<? extends DataObject> clazz : scanClasses) {
            try {
                handleDataObjectClass(clazz);
            } catch (Exception e) {
                log.error("Failed to detect/rebuild duplicated index for {}", clazz, e);
            }
        }
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(120, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            } 
        } catch (Exception e) {
            executor.shutdownNow();
        }
        
        log.info("Totally rebuild index count: {}", totalProcessedIndex);
        log.info("Finish run migration handler RebuildIndexDuplicatedCFName");
        log.info("Total seconds: {}", (System.currentTimeMillis() - beginTime)/1000);
    }

    public void handleDataObjectClass(Class<? extends DataObject> clazz) throws Exception {
        log.info("proccess model class {}", clazz);

        InternalDbClient dbClient = (InternalDbClient) getDbClient();
        DataObjectType doType = TypeMap.getDoType(clazz);
        
        Keyspace keyspace = dbClient.getLocalContext().getKeyspace();

        ColumnFamilyQuery<String, CompositeColumnName> query = keyspace.prepareQuery(doType.getCF());
        OperationResult<Rows<String, CompositeColumnName>> result = query.getAllRows().setRowLimit(100).execute();
        int totalCount = 0;
        List<Row<String, CompositeColumnName>> rows = new ArrayList<Row<String, CompositeColumnName>>();
        for (Row<String, CompositeColumnName> objRow : result.getResult()) {
            boolean inactiveObject = false;
            totalCount++;

            for (Column<CompositeColumnName> column : objRow.getColumns()) {
                if (DataObject.INACTIVE_FIELD_NAME.equals(column.getName().getOne()) && column.getBooleanValue()) {
                    inactiveObject = true;
                    break;
                }
            }

            if (inactiveObject) {
                continue;
            }

            rows.add(objRow);
            if (rows.size() > REBUILD_INDEX_BATCH_SIZE) {
                try {
                    executor.submit(new RebuildIndexTask(doType, rows, keyspace));
                    rows = new ArrayList<Row<String, CompositeColumnName>>();
                } catch (Exception e) {
                    log.warn("Failed to submit rebuild index task, this may be caused by thread pool is full, try in next round", e);
                }
            }
            
        }
        
        executor.submit(new RebuildIndexTask(doType, rows, keyspace));
        
        log.info("Total data object count is {} for model {}", totalCount, clazz.getName());
        return;
    }

    private void rebuildIndex(DataObjectType doType, ColumnField columnField, Object value, String rowKey,
            Column<CompositeColumnName> column, RowMutator rowMutator) throws InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        
        rowMutator.getRecordColumnList(doType.getCF(), rowKey).deleteColumn(column.getName());
        
        rowMutator.resetTimeUUIDStartTime(TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID()));
        DataObject dataObject = DataObject.createInstance(doType.getDataObjectClass(), URI.create(rowKey));
        dataObject.trackChanges();
        
        columnField.getPropertyDescriptor().getWriteMethod().invoke(dataObject, value);
        columnField.serialize(dataObject, rowMutator);
    }

    public int getTotalProcessedIndexCount() {
        return totalProcessedIndex.get();
    }
    
    class RebuildIndexTask implements Runnable {
        private DataObjectType doType;
        private List<Row<String, CompositeColumnName>> rows;
        private Keyspace keyspace;
        
        public RebuildIndexTask(DataObjectType doType, List<Row<String, CompositeColumnName>> rows, Keyspace keyspace) {
            this.doType = doType;
            this.rows = rows;
            this.keyspace = keyspace;
        }
        
        @Override
        public void run() {
            int totalCleanupCount = 0;
            RowMutator rowMutator = new RowMutator(keyspace, false);
            for (Row<String, CompositeColumnName> objRow : rows) {
                try {
                    Map<FieldValueTimeUUIDPair, CompositeColumnName> valueColumnMap = new HashMap<FieldValueTimeUUIDPair, CompositeColumnName>();
                    DataObject dataObject = DataObject.createInstance(doType.getDataObjectClass(), URI.create(objRow.getKey()));
                    for (Column<CompositeColumnName> column : objRow.getColumns()) {
                        if (!fieldNames.contains(column.getName().getOne())) {
                            continue;
                        }

                        ColumnField columnField = doType.getColumnField(column.getName().getOne());
                        columnField.deserialize(column, dataObject);
                        Object valueObject = columnField.getPropertyDescriptor().getReadMethod().invoke(dataObject, null);

                        if (valueObject == null || column.getName().getTimeUUID() == null) {
                            continue;
                        }

                        FieldValueTimeUUIDPair key = null;
                        key = new FieldValueTimeUUIDPair(valueObject, column.getName().getTimeUUID());

                        if (valueColumnMap.containsKey(key)) {
                            totalCleanupCount++;
                            rebuildIndex(doType, columnField, valueObject, objRow.getKey(), column, rowMutator);
                        } else {
                            valueColumnMap.put(key, column.getName());
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to proccess Data Object: ", e);
                }
            }
            
            if (totalCleanupCount > 0) {
                rowMutator.execute();
            }
            
            totalProcessedIndex.getAndAdd(totalCleanupCount);
        }
    }
}

class FieldValueTimeUUIDPair {
    private Object fieldValue;
    private UUID timeUUID;

    public FieldValueTimeUUIDPair(Object fieldValue, UUID timeUUID) {
        this.fieldValue = fieldValue;
        this.timeUUID = timeUUID;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fieldValue == null) ? 0 : fieldValue.hashCode());
        result = prime * result + ((timeUUID == null) ? 0 : timeUUID.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FieldValueTimeUUIDPair other = (FieldValueTimeUUIDPair) obj;
        if (fieldValue == null) {
            if (other.fieldValue != null)
                return false;
        } else if (!fieldValue.equals(other.fieldValue))
            return false;
        if (timeUUID == null) {
            if (other.timeUUID != null)
                return false;
        } else if (!timeUUID.equals(other.timeUUID))
            return false;
        return true;
    }
}
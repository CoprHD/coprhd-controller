/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.SchemaRecord;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.DuplicatedIndexCFDetector;
import com.emc.storageos.db.client.upgrade.DuplicatedIndexCFDetector.DuplciatedIndexDataObject;
import com.emc.storageos.db.client.upgrade.DuplicatedIndexCFDetector.IndexCFKey;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.db.client.util.KeyspaceUtil;
import com.emc.storageos.db.common.DbSchemaChecker;
import com.emc.storageos.db.common.schema.DbSchemas;
import com.emc.storageos.db.common.schema.FieldInfo;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.query.ColumnFamilyQuery;
import com.netflix.astyanax.util.TimeUUIDUtils;

public class RebuildIndexDuplicatedCFNameMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(RebuildIndexDuplicatedCFNameMigration.class);
    private int totalProcessedClassCount = 0;
    private int totalProcessedDataObjectCount = 0;

    @Override
    public void process() throws MigrationCallbackException {
        log.info("Begin to run migration handler RebuildIndexDuplicatedCFName");
        long beginTime = System.currentTimeMillis();

        InternalDbClient dbClient = (InternalDbClient) getDbClient();

        TreeMap<Long, String> schemaVersions = (TreeMap) dbClient.querySchemaVersions();
        String version = schemaVersions.lastEntry().getValue();
        log.info("query latest schema version {}", version);

        SchemaRecord schemaRecord = dbClient.querySchemaRecord(version);
        try (BufferedReader reader = new BufferedReader(new StringReader(schemaRecord.getSchema()))) {
            DbSchemas dbSchema = DbSchemaChecker.unmarshalSchemas(version, reader);

            DuplicatedIndexCFDetector detector = new DuplicatedIndexCFDetector();
            List<DuplciatedIndexDataObject> duplciatedIndexDataObjects = detector.findDuplicatedIndexCFNames(dbSchema);
            Map<String, List<DuplciatedIndexDataObject>> duplciatedIndexDataObjectMap = new HashMap<String, List<DuplciatedIndexDataObject>>();

            for (DuplciatedIndexDataObject duplciatedIndexDataObject : duplciatedIndexDataObjects) {
                if (!duplciatedIndexDataObjectMap.containsKey(duplciatedIndexDataObject.getClassName())) {
                    duplciatedIndexDataObjectMap.put(duplciatedIndexDataObject.getClassName(), new ArrayList<DuplciatedIndexDataObject>());
                }
                duplciatedIndexDataObjectMap.get(duplciatedIndexDataObject.getClassName()).add(duplciatedIndexDataObject);
                //handleDataObjectClass(duplciatedIndexDataObject);
            }
            
            for (Entry<String, List<DuplciatedIndexDataObject>> entry : duplciatedIndexDataObjectMap.entrySet()) {
                handleDataObjectClass(entry.getKey(), entry.getValue());
            }
            
            log.info("Total Classes: {}", totalProcessedClassCount);
            log.info("Total Data Object: {}", totalProcessedDataObjectCount);
        } catch (Exception e) {
            log.error("Failed to fun migration handler RebuildIndexDuplicatedCFName {}", e);
        }

        log.info("Finish run migration handler RebuildIndexDuplicatedCFName");
        log.info("Total seconds: {}", (System.currentTimeMillis() - beginTime)/1000);
    }

    public int handleDataObjectClass(String className, List<DuplciatedIndexDataObject> duplciatedIndexDataObjects) throws Exception {
        log.info("proccess model class {}", className);

        InternalDbClient dbClient = (InternalDbClient) getDbClient();
        DataObjectType doType = TypeMap.getDoType((Class<? extends DataObject>) Class.forName(className));
        
        if (KeyspaceUtil.isGlobal(doType.getDataObjectClass())) {
            log.info("Skip global model {}", className);
            return 0;
        }
        
        int totalProcessedIndex = 0;
        totalProcessedClassCount++;
        Keyspace keyspace = dbClient.getLocalContext().getKeyspace();

        ColumnFamilyQuery<String, CompositeColumnName> query = keyspace.prepareQuery(doType.getCF());
        OperationResult<Rows<String, CompositeColumnName>> result = query.getAllRows().setRowLimit(100).execute();
        int totalCount = 0;
        for (Row<String, CompositeColumnName> objRow : result.getResult()) {
            boolean inactiveObject = false;
            totalProcessedDataObjectCount++;
            totalCount++;

            for (Column<CompositeColumnName> column : objRow.getColumns()) {
                if (DataObject.INACTIVE_FIELD_NAME.equals(column.getName().getOne()) && column.getBooleanValue()) {
                    inactiveObject = true;
                    break;
                }
            }

            // skip inactive data object
            if (inactiveObject) {
                continue;
            }

            for (DuplciatedIndexDataObject duplciatedIndexDataObject : duplciatedIndexDataObjects) {
                for (Entry<IndexCFKey, List<FieldInfo>> entry : duplciatedIndexDataObject.getIndexFieldsMap().entrySet()) {
                    totalProcessedIndex += proccessDataObjectTypeByIndexCF(doType, entry, objRow, keyspace);
                }
            }
        }
        
        log.info("Total data object count is {} for model {}", totalCount, className);
        return totalProcessedIndex;
    }

    public int proccessDataObjectTypeByIndexCF(DataObjectType doType, Entry<IndexCFKey, List<FieldInfo>> entry,
            Row<String, CompositeColumnName> objRow, Keyspace keyspace) throws InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        Set<String> fields = new HashSet<String>();
        Map<FieldValueTimeUUIDPair, CompositeColumnName> valueColumnMap = new HashMap<FieldValueTimeUUIDPair, CompositeColumnName>();
        int totalCleanupCount = 0;

        for (FieldInfo field : entry.getValue()) {
            fields.add(field.getName());
        }

        DataObject dataObject = DataObject.createInstance(doType.getDataObjectClass(), URI.create(objRow.getKey()));
        for (Column<CompositeColumnName> column : objRow.getColumns()) {
            if (!fields.contains(column.getName().getOne())) {
                continue;
            }

            ColumnField columnField = doType.getColumnField(column.getName().getOne());
            columnField.deserialize(column, dataObject);
            Object valueObject = columnField.getPropertyDescriptor().getReadMethod().invoke(dataObject, null);

            if (valueObject == null || column.getName().getTimeUUID() == null) {
                continue;
            }
            
            FieldValueTimeUUIDPair key = null;
            if (columnField.getType() == ColumnField.ColumnType.NamedURI || columnField.getType() == ColumnField.ColumnType.Primitive) { 
                key = new FieldValueTimeUUIDPair(valueObject, column.getName().getTimeUUID());
            } else if (columnField.getType() == ColumnField.ColumnType.TrackingSet) {
                key = new FieldValueTimeUUIDPair(column.getName().getTwo(), column.getName().getTimeUUID());
            } else {
                continue;
            }
            
            if (valueColumnMap.containsKey(key)) {
                totalCleanupCount++;
                /*log.info("Found duplicated index value: {}", key);
                log.info("Column1: {}", column.getName());
                log.info("Column2: {}", valueColumnMap.get(key));*/
                rebuildIndex(doType, columnField, valueObject, objRow.getKey(), column, keyspace);
            } else {
                valueColumnMap.put(key, column.getName());
            }
        }

        if (totalCleanupCount > 0) {
            log.info("Total cleanup {} for {}", totalCleanupCount, objRow.getKey());
        }
        return totalCleanupCount;
    }

    private void rebuildIndex(DataObjectType doType, ColumnField columnField, Object value, String rowKey,
            Column<CompositeColumnName> column, Keyspace keyspace) throws InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        RowMutator rowMutator = new RowMutator(keyspace, false, TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID()));
        rowMutator.getRecordColumnList(doType.getCF(), rowKey).deleteColumn(column.getName());

        DataObject dataObject = DataObject.createInstance(doType.getDataObjectClass(), URI.create(rowKey));
        dataObject.trackChanges();
        if (columnField.getType() == ColumnField.ColumnType.TrackingSet) {
            StringSet stringSet = new StringSet();
            stringSet.add(column.getStringValue());
            value = stringSet;
        }
        columnField.getPropertyDescriptor().getWriteMethod().invoke(dataObject, value);
        columnField.serialize(dataObject, rowMutator);
        
        rowMutator.execute();
    }

    public int getTotalProccessedClassCount() {
        return totalProcessedClassCount;
    }   
}

class FieldValueTimeUUIDPair {
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("FieldValueTimeUUIDPair [fieldValue=");
        builder.append(fieldValue);
        builder.append(", timeUUID=");
        builder.append(timeUUID);
        builder.append("]");
        return builder.toString();
    }

    private Object fieldValue;
    private UUID timeUUID;

    public FieldValueTimeUUIDPair(Object fieldValue, UUID timeUUID) {
        super();
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

/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.SchemaRecord;
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

    @Override
    public void process() throws MigrationCallbackException {
        log.info("Begin to run migration handler RebuildIndexDuplicatedCFName");

        InternalDbClient dbClient = (InternalDbClient) getDbClient();

        TreeMap<Long, String> schemaVersions = (TreeMap) dbClient.querySchemaVersions();
        String version = schemaVersions.lastEntry().getValue();
        log.info("query latest schema version {}", version);

        SchemaRecord schemaRecord = dbClient.querySchemaRecord(version);
        try (BufferedReader reader = new BufferedReader(new StringReader(schemaRecord.getSchema()))) {
            DbSchemas dbSchema = DbSchemaChecker.unmarshalSchemas(version, reader);

            DuplicatedIndexCFDetector detector = new DuplicatedIndexCFDetector();
            List<DuplciatedIndexDataObject> duplciatedIndexDataObjects = detector.findDuplicatedIndexCFNames(dbSchema);

            for (DuplciatedIndexDataObject duplciatedIndexDataObject : duplciatedIndexDataObjects) {
                handleDataObjectClass(duplciatedIndexDataObject);
            }

            log.info("Total Classes: {}" + duplciatedIndexDataObjects.size());
        } catch (Exception e) {
            log.error("Failed to fun migration handler RebuildIndexDuplicatedCFName {}", e);
        }

        log.info("Finish run migration handler RebuildIndexDuplicatedCFName");
    }

    public void handleDataObjectClass(DuplciatedIndexDataObject duplciatedIndexDataObject) throws Exception {
        log.info("proccess model class {}", duplciatedIndexDataObject.getClassName());

        InternalDbClient dbClient = (InternalDbClient) getDbClient();
        DataObjectType doType = TypeMap.getDoType((Class<? extends DataObject>) Class.forName(duplciatedIndexDataObject.getClassName()));
        Keyspace keyspace = KeyspaceUtil.isGlobal(doType.getDataObjectClass()) ?
                dbClient.getGeoContext().getKeyspace() : dbClient.getLocalContext().getKeyspace();

        ColumnFamilyQuery<String, CompositeColumnName> query = keyspace.prepareQuery(doType.getCF());
        OperationResult<Rows<String, CompositeColumnName>> result = query.getAllRows().setRowLimit(100).execute();

        for (Row<String, CompositeColumnName> objRow : result.getResult()) {
            boolean inactiveObject = false;

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

            for (Entry<IndexCFKey, List<FieldInfo>> entry : duplciatedIndexDataObject.getIndexFieldsMap().entrySet()) {
                proccessDataObjectTypeByIndexCF(doType, entry, objRow, keyspace);
            }
        }
    }

    public int proccessDataObjectTypeByIndexCF(DataObjectType doType, Entry<IndexCFKey, List<FieldInfo>> entry,
            Row<String, CompositeColumnName> objRow, Keyspace keyspace) throws InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        Set<String> fields = new HashSet<String>();
        Map<String, CompositeColumnName> valueColumnMap = new HashMap<String, CompositeColumnName>();
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

            String key = String.format("%s:%s", valueObject.toString(), column.getName().getTimeUUID());
            if (valueColumnMap.containsKey(key)) {
                totalCleanupCount++;
                rebuildIndex(doType, columnField, valueObject, objRow.getKey(), column, keyspace);
            } else {
                valueColumnMap.put(key, column.getName());
            }
        }

        log.info("Total cleanup {} for {}", totalCleanupCount, objRow.getKey());
        return totalCleanupCount;
    }

    private void rebuildIndex(DataObjectType doType, ColumnField columnField, Object value, String rowKey,
            Column<CompositeColumnName> column, Keyspace keyspace) throws InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        RowMutator rowMutator = new RowMutator(keyspace, false, TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID()));
        rowMutator.getRecordColumnList(doType.getCF(), rowKey).deleteColumn(column.getName());

        DataObject dataObject = DataObject.createInstance(doType.getDataObjectClass(), URI.create(rowKey));
        dataObject.trackChanges();
        columnField.getPropertyDescriptor().getWriteMethod().invoke(dataObject, value);
        columnField.serialize(dataObject, rowMutator);
        
        rowMutator.execute();
    }
}

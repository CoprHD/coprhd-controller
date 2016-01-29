/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade;

import com.emc.storageos.db.client.constraint.DecommissionedConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.*;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.SchemaRecord;
import com.emc.storageos.db.client.util.KeyspaceUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.ddl.SchemaChangeResult;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.query.RowSliceQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

/**
 * Internal db client used for upgrade migrations
 */
public class InternalDbClient extends DbClientImpl {
    private static final Logger log = LoggerFactory.getLogger(InternalDbClient.class);

    private static final long MAX_SCHEMA_WAIT_MS = 60 * 1000 * 10;
    private static final int RETRY_INTERVAL = 1000;

    private List<URI> getNextBatch(Iterator<URI> it) {
        List<URI> uris = new ArrayList<URI>(DEFAULT_PAGE_SIZE);
        while (it.hasNext() && uris.size() < DEFAULT_PAGE_SIZE) {
            uris.add(it.next());
        }
        return uris;
    }

    public <T extends DataObject> void removeFieldIndex(Class<T> clazz, String fieldName, String indexCf) {
        log.debug("removing index records for " + clazz.getSimpleName() + ":" + fieldName + " from index cf table: " + indexCf);
        DataObjectType doType = TypeMap.getDoType(clazz);
        if (doType == null) {
            throw new IllegalArgumentException();
        }
        ColumnField columnField = doType.getColumnField(fieldName);
        if (columnField == null) {
            throw new IllegalArgumentException();
        }

        List<URI> allrecs = queryByType(clazz, false);
        Keyspace ks = getKeyspace(clazz);
        Iterator<URI> recIt = allrecs.iterator();
        List<URI> batch = getNextBatch(recIt);
        while (!batch.isEmpty()) {
            Rows<String, CompositeColumnName> rows = queryRowsWithAColumn(ks, batch, doType.getCF(),
                    columnField);
            Iterator<Row<String, CompositeColumnName>> it = rows.iterator();
            Map<String, List<Column<CompositeColumnName>>> removeList = new HashMap<String, List<Column<CompositeColumnName>>>();
            while (it.hasNext()) {
                Row<String, CompositeColumnName> row = it.next();
                if (row.getColumns().size() == 0) {
                    continue;
                }
                Iterator<Column<CompositeColumnName>> columnIterator = row.getColumns().iterator();
                while (columnIterator.hasNext()) {
                    Column<CompositeColumnName> column = columnIterator.next();
                    if (removeList.get(row.getKey()) == null) {
                        removeList.put(row.getKey(), new ArrayList<Column<CompositeColumnName>>());
                    }
                    removeList.get(row.getKey()).add(column);
                }
            }
            RowMutator mutator = new RowMutator(ks, retryFailedWriteWithLocalQuorum);
            _indexCleaner.removeOldIndex(mutator, doType, removeList, indexCf);
            batch = getNextBatch(recIt);
        }
    }

    // TODO geo : migration only works for local keyspace; need to expand to use local or global

    public <T extends DataObject> void generateFieldIndex(Class<T> clazz, String fieldName) {
        DataObjectType doType = TypeMap.getDoType(clazz);
        if (doType == null) {
            throw new IllegalArgumentException();
        }
        ColumnField columnField = doType.getColumnField(fieldName);
        if (columnField == null) {
            throw new IllegalArgumentException();
        }

        List<URI> allrecs = queryByType(clazz, false);
        Keyspace ks = getKeyspace(clazz);
        Iterator<URI> recIt = allrecs.iterator();
        List<URI> batch = getNextBatch(recIt);
        while (!batch.isEmpty()) {
            Rows<String, CompositeColumnName> rows = queryRowsWithAColumn(ks, batch, doType.getCF(),
                    columnField);
            List<T> objects = new ArrayList<T>(rows.size());
            Iterator<Row<String, CompositeColumnName>> it = rows.iterator();
            while (it.hasNext()) {
                Row<String, CompositeColumnName> row = it.next();
                try {
                    if (row.getColumns().size() == 0) {
                        continue;
                    }
                    DataObject obj = DataObject.createInstance(clazz, URI.create(row.getKey()));
                    obj.trackChanges();
                    Iterator<Column<CompositeColumnName>> columnIterator = row.getColumns().iterator();
                    while (columnIterator.hasNext()) {
                        Column<CompositeColumnName> column = columnIterator.next();
                        columnField.deserialize(column, obj);
                    }
                    // set changed for ChangeTracking structures
                    columnField.setChanged(obj);
                    objects.add(clazz.cast(obj));
                } catch (final InstantiationException e) {
                    throw DatabaseException.fatals.queryFailed(e);
                } catch (final IllegalAccessException e) {
                    throw DatabaseException.fatals.queryFailed(e);
                }
            }
            updateAndReindexObject(objects);
            batch = getNextBatch(recIt);
        }
    }

    public void persistSchemaRecord(SchemaRecord record) throws DatabaseException {
        try {
            MutationBatch batch = getLocalKeyspace().prepareMutationBatch();
            SchemaRecordType type = TypeMap.getSchemaRecordType();
            type.serialize(batch, record);
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    public SchemaRecord querySchemaRecord(String version) throws DatabaseException {
        try {
            SchemaRecordType type = TypeMap.getSchemaRecordType();
            RowSliceQuery<String, String> query = getLocalKeyspace()
                    .prepareQuery(type.getCf())
                    .getRowSlice(version);
            Rows<String, String> rows = query.execute().getResult();
            if (rows == null || rows.isEmpty()) {
                return null;
            }
            return type.deserialize(rows.iterator().next());
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    public List<URI> getUpdateList(Class<? extends DataObject> clazz) throws DatabaseException {
        DataObjectType doType = TypeMap.getDoType(clazz);
        if (doType == null) {
            throw new IllegalArgumentException();
        }

        List<URI> keyList = queryByType(clazz, false);
        List<URI> inmemKeyList = new ArrayList<URI>();
        for (URI uri : keyList) {
            inmemKeyList.add(uri);
        }
        log.info("CF({}): row count by getting all rows= {}", clazz.getSimpleName(), inmemKeyList.size());

        URIQueryResultList inactiveResult = new URIQueryResultList();
        DecommissionedConstraint constraint = DecommissionedConstraint.Factory.getAllObjectsConstraint(clazz, true);
        constraint.setKeyspace(getKeyspace(clazz));
        constraint.execute(inactiveResult);

        int count = 0;
        Iterator<URI> inactiveKeyIter = inactiveResult.iterator();
        while (inactiveKeyIter.hasNext()) {
            inmemKeyList.remove(inactiveKeyIter.next());
            count++;
        }
        log.info("CF({}): inactive key count= {}", clazz.getSimpleName(), count);

        URIQueryResultList activeResult = new URIQueryResultList();
        constraint = DecommissionedConstraint.Factory.getAllObjectsConstraint(clazz, false);
        constraint.setKeyspace(getKeyspace(clazz));
        constraint.execute(activeResult);

        count = 0;
        Iterator<URI> activeKeyIter = activeResult.iterator();
        while (activeKeyIter.hasNext()) {
            inmemKeyList.remove(activeKeyIter.next());
            count++;
        }
        log.info("CF({}): active key count: {}", clazz.getSimpleName(), count);

        return inmemKeyList;
    }

    // only used during migration stage in single thread, so it's safe to suppress
    @SuppressWarnings("findbugs:IS2_INCONSISTENT_SYNC")
    public <T extends DataObject> void migrateToGeoDb(Class<T> clazz) {
        DataObjectType doType = TypeMap.getDoType(clazz);
        if (doType == null) {
            throw new IllegalArgumentException();
        }

        if (!KeyspaceUtil.isGlobal(clazz)) {
            throw new IllegalArgumentException(String.format("CF %s is not a global resource",
                    clazz.getName()));
        }
        doType.setEncryptionProvider(_geoEncryptionProvider); // this CF ensured to be a global resource

        // find all the records of CF <T> in local db, similar to queryByType(clazz, false)
        URIQueryResultList result = new URIQueryResultList();
        DecommissionedConstraint constraint =
                DecommissionedConstraint.Factory.getAllObjectsConstraint(clazz, null);
        constraint.setKeyspace(localContext.getKeyspace());
        constraint.execute(result);

        Iterator<URI> recIt = result.iterator();
        List<URI> batch = getNextBatch(recIt);
        while (!batch.isEmpty()) {
            Rows<String, CompositeColumnName> rows = queryRowsWithAllColumns(
                    localContext.getKeyspace(), batch, doType.getCF());
            Iterator<Row<String, CompositeColumnName>> it = rows.iterator();
            while (it.hasNext()) {
                Row<String, CompositeColumnName> row = it.next();

                try {
                    if (row.getColumns().size() == 0) {
                        continue;
                    }
                    // can't simply use doType.deserialize(clazz, row, cleanList) below
                    // since the DataObject instance retrieved in this way doesn't have
                    // change tracking information within and nothing gets persisted into
                    // db in the end.
                    log.info("Migrating record {} to geo db", row.getKey());
                    DataObject obj = DataObject.createInstance(clazz, URI.create(row.getKey()));
                    obj.trackChanges();
                    Iterator<Column<CompositeColumnName>> columnIterator = row.getColumns().iterator();
                    while (columnIterator.hasNext()) {
                        Column<CompositeColumnName> column = columnIterator.next();
                        ColumnField columnField = doType.getColumnField(column.getName().getOne());
                        if (columnField.isEncrypted()) {
                            // Decrypt using the local encryption provider and later
                            // encrypt it again using the geo encryption provider
                            columnField.deserializeEncryptedColumn(column, obj,
                                    _encryptionProvider);
                        } else {
                            columnField.deserialize(column, obj);
                        }
                        // set changed for ChangeTracking structures
                        columnField.setChanged(obj);
                    }

                    // persist the object into geo db, similar to createObject(objects)
                    // only that we need to specify the keyspace explicitly here
                    // also we shouldn't overwrite the creation time
                    RowMutator mutator = new RowMutator(geoContext.getKeyspace(), retryFailedWriteWithLocalQuorum);
                    doType.serialize(mutator, obj);
                    mutator.executeRecordFirst();
                } catch (final InstantiationException e) {
                    throw DatabaseException.fatals.queryFailed(e);
                } catch (final IllegalAccessException e) {
                    throw DatabaseException.fatals.queryFailed(e);
                }
            }
            batch = getNextBatch(recIt);
        }
    }

    public void rebuildCf(String cf) {
        try {
            Properties props = getLocalKeyspace().getColumnFamilyProperties(cf);
            OperationResult<SchemaChangeResult> dropCFResult = getLocalKeyspace().dropColumnFamily(cf);
            waitForSchemaChange(dropCFResult);
            // bloom filter can not be 0 starting at version 1.2. Otherwise CF create throws an exception
            // see CASSANDRA-5013
            // In this case set value for Bloom Filter as 0.01 which is the default value for SizeTieredCompactionStrategy
            String value = (String) props.get("bloom_filter_fp_chance");
            double fpValue;
            if (value == null || value.isEmpty()) {
                value = "0.01";
            }
            else {

                try {
                    fpValue = Double.parseDouble(value);
                    if (fpValue < 0.000001) {
                        fpValue = 0.01;
                    }
                } catch (Exception ex) {
                    fpValue = 0.01;
                }
                value = Double.toString(fpValue);
            }
            log.info("Setting value for Bloom Filter to " + value);
            props.setProperty("bloom_filter_fp_chance", value);
            OperationResult<SchemaChangeResult> createCFResult = getLocalKeyspace().createColumnFamily(props);
            waitForSchemaChange(createCFResult);
        } catch (ConnectionException connEx) {
            log.error("Failed to recreate columnFamily : " + cf);
            DatabaseException.retryables.connectionFailed(connEx);
        }
    }

    public void resetFields(Class<? extends DataObject> clazz, Map<String, ColumnField> setFields, boolean ignore) throws Exception {
        DataObjectType doType = TypeMap.getDoType(clazz);
        if (doType == null) {
            throw new IllegalArgumentException();
        }
        try {
            Keyspace ks = getKeyspace(clazz);
            OperationResult<Rows<String, CompositeColumnName>> result =
                    ks.prepareQuery(doType.getCF()).getAllRows().setRowLimit(DEFAULT_PAGE_SIZE).execute();
            Iterator<Row<String, CompositeColumnName>> it = result.getResult().iterator();
            RemovedColumnsList removedList = new RemovedColumnsList();
            List<DataObject> objects = new ArrayList<>(DEFAULT_PAGE_SIZE);
            String key = null;
            Exception lastEx = null;

            while (it.hasNext()) {
                try {
                    Row<String, CompositeColumnName> row = it.next();
                    if (row.getColumns().size() == 0) {
                        continue;
                    }
                    key = row.getKey();
                    DataObject obj = DataObject.createInstance(clazz, URI.create(key));
                    obj.trackChanges();
                    objects.add(obj);
                    Iterator<Column<CompositeColumnName>> columnIterator = row.getColumns().iterator();
                    while (columnIterator.hasNext()) {
                        Column<CompositeColumnName> column = columnIterator.next();
                        ColumnField columnField = setFields.get(column.getName().getOne());
                        if (columnField != null) {
                            columnField.deserialize(column, obj);
                            removedList.add(key, column);
                        }
                    }

                    if (objects.size() == DEFAULT_PAGE_SIZE) {
                        RowMutator mutator = new RowMutator(ks, retryFailedWriteWithLocalQuorum);
                        _indexCleaner.removeColumnAndIndex(mutator, doType, removedList);
                        persistObject(objects);
                        objects.clear();
                        removedList.clear();
                    }
                } catch (Exception e) {
                    String message = String.format("DB migration failed reason: reset data key='%s'", key);

                    log.error(message);
                    log.error("e=", e);

                    if (ignore) {
                        lastEx = e;
                        continue;
                    }

                    throw e;
                }
            }

            if (lastEx != null) {
                throw lastEx;
            }

            if (!objects.isEmpty()) {
                RowMutator mutator = new RowMutator(ks, retryFailedWriteWithLocalQuorum);
                _indexCleaner.removeColumnAndIndex(mutator, doType, removedList);
                persistObject(objects);
            }
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        } catch (final InstantiationException e) {
            throw DatabaseException.fatals.queryFailed(e);
        } catch (final IllegalAccessException e) {
            throw DatabaseException.fatals.queryFailed(e);
        }
    }

    private void waitForSchemaChange(final OperationResult<SchemaChangeResult> result) {
        String schemaVersion = result.getResult().getSchemaId();
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < MAX_SCHEMA_WAIT_MS) {
            Map<String, List<String>> versions;
            try {
                versions = getLocalKeyspace().describeSchemaVersions();
            } catch (final ConnectionException e) {
                throw DatabaseException.retryables.connectionFailed(e);
            }
            if (versions.size() == 1 && versions.containsKey(schemaVersion)) {
                log.info("schema version sync to: {} done", schemaVersion);
                return;
            }
            try {
                Thread.sleep(RETRY_INTERVAL);
            } catch (InterruptedException e) {
                log.warn("DB keyspace verification interrupted, ignore", e);
            }
        }
        log.warn("Unable to sync schema version {}", schemaVersion);
    }
}

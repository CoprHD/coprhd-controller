/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.emc.storageos.db.client.model.uimodels.ExecutionLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.PasswordHistory;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.google.common.collect.Lists;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.connectionpool.exceptions.NotFoundException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.CqlResult;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.query.ColumnFamilyQuery;
import com.netflix.astyanax.query.PreparedCqlQuery;
import com.netflix.astyanax.query.RowQuery;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.thrift.model.ThriftCqlResultImpl;
import com.netflix.astyanax.util.RangeBuilder;
import com.netflix.astyanax.util.TimeUUIDUtils;

public class DbConsistencyCheckerHelper {
    private static final Logger _log = LoggerFactory.getLogger(DbConsistencyCheckerHelper.class);
    public static final String MSG_OBJECT_ID_START = "\nStart to check DataObject records id that is illegal.\n";
    public static final String MSG_OBJECT_ID_END = "\nFinish to check DataObject records id: totally checked %d data CFs, %d corrupted rows found.\n";
    public static final String MSG_OBJECT_ID_END_SPECIFIED = "\nFinish to check DataObject records id for CF %s, %d corrupted rows found.\n";
    public static final String MSG_OBJECT_INDICES_START = "\nStart to check DataObject records that the related index is missing.\n";
    public static final String MSG_OBJECT_INDICES_END = "Finish to check DataObject records index: totally checked %d data CFs, %d corrupted rows found.\n";
    public static final String MSG_OBJECT_INDICES_END_SPECIFIED = "\nFinish to check DataObject records index for CF %s, %d corrupted rows found.\n";
    public static final String MSG_INDEX_OBJECTS_START = "\nStart to check INDEX data that the related object records are missing.\n";
    public static final String MSG_INDEX_OBJECTS_END = "Finish to check INDEX records: totally checked %d indices and %d corrupted rows found.\n";
    public static final String MSG_INDEX_OBJECTS_END_SPECIFIED = "\nFinish to check INDEX records: totally checked %d indices for CF %s and %d corrupted rows found.\n";

    private static final String DELETE_INDEX_CQL = "delete from \"%s\" where key='%s' and column1='%s' and column2='%s' and column3='%s' and column4='%s' and column5=%s;";
    private static final String DELETE_INDEX_CQL_WITHOUT_UUID = "delete from \"%s\" where key='%s' and column1='%s' and column2='%s' and column3='%s' and column4='%s';";
    private static final String DELETE_ORDER_INDEX_CQL = "delete from \"%s\" where key='%s' and column1='%s' and column2=%s and column3='%s' and column4='%s' and column5=%s;";
    private static final String DELETE_ORDER_INDEX_CQL_WITHOUT_UUID = "delete from \"%s\" where key='%s' and column1='%s' and column2=%s and column3='%s' and column4='%s';";
    private static final String CQL_QUERY_SCHEMA_VERSION_TIMESTAMP = "SELECT key, writetime(value) FROM \"SchemaRecord\";";
    
    private static final int INDEX_OBJECTS_BATCH_SIZE = 1000;
    private static final int THREAD_POOL_QUEUE_SIZE = 50;
    private static final int WAITING_TIME_FOR_QUEUE_FULL_MS = 3000;
    private static final int THRESHHOLD_FOR_OUTPUT_DEBUG = 10000;

    private DbClientImpl dbClient;
    private Set<Class<? extends DataObject>> excludeClasses = new HashSet<Class<? extends DataObject>>(Arrays.asList(PasswordHistory.class));
    private final Set<String> skipCheckCFs = new HashSet<>(Arrays.asList(ExecutionState.class.getSimpleName(), ExecutionLog.class.getSimpleName(), ExecutionTaskLog.class.getSimpleName()));
    private volatile Map<Long, String> schemaVersionsTime;
    private BlockingQueue<Runnable> blockingQueue = new ArrayBlockingQueue<Runnable>(THREAD_POOL_QUEUE_SIZE);
    private ThreadPoolExecutor executor = new ThreadPoolExecutor(5, 20, 50, TimeUnit.MILLISECONDS, blockingQueue);
    private boolean doubleConfirmed = true;
    
    public DbConsistencyCheckerHelper() {
    }

    public DbConsistencyCheckerHelper(DbClientImpl dbClient) {
        this.dbClient = dbClient;
        initSchemaVersions();
    }

    /**
     * Find out all rows in DataObject CFs that can't be deserialized,
     * such as such as object id cannot be converted to URI.
     *
     * @return number of corrupted rows
     */
    public int checkDataObject(DataObjectType doType, boolean toConsole) {
        int dirtyCount = 0;
        Class<? extends DataObject> dataObjectClass = doType.getDataObjectClass();
        
		_log.info("Check CF {}", dataObjectClass.getName());
        
        if (excludeClasses.contains(dataObjectClass)) {
        	_log.info("Skip CF {} since its URI is special", dataObjectClass);
        	return 0;
        }
        
        try {
            OperationResult<Rows<String, CompositeColumnName>> result = dbClient.getKeyspace(
                    dataObjectClass).prepareQuery(doType.getCF())
                    .getAllRows().setRowLimit(dbClient.DEFAULT_PAGE_SIZE)
                    .withColumnRange(new RangeBuilder().setLimit(1).build())
                    .execute();
            for (Row<String, CompositeColumnName> row : result.getResult()) {
            	try {
	            	if (!isValidDataObjectKey(URI.create(row.getKey()), dataObjectClass)) {
	            		dirtyCount++;
	    		        logMessage(String.format("Inconsistency found: Row key '%s' failed to convert to URI in CF %s",
	    		                row.getKey(), dataObjectClass.getName()), true, toConsole);
	            	}
            	} catch (Exception ex) {
            		dirtyCount++;
            		logMessage(String.format("Inconsistency found: Row key '%s' failed to convert to URI in CF %s with exception %s",
                            row.getKey(), dataObjectClass.getName(),
                            ex.getMessage()), true, toConsole);
            	}
            }

        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }

        return dirtyCount;
    }

    /**
     * Scan all the data object records, to find out the object record is existing but the related index is missing.
     *
     * @param doType
     * @param toConsole whether print out in the console
     * @return the number of corrupted data
     * @throws ConnectionException
     */
    public void checkCFIndices(DataObjectType doType, boolean toConsole, CheckResult checkResult) throws ConnectionException {
        initSchemaVersions();
        Class objClass = doType.getDataObjectClass();
        if (skipCheckCFs.contains(objClass.getSimpleName())) {
            _log.info("Skip checking CF {}", objClass);
            return;
        } else {
            _log.info("Check Data Object CF {} with double confirmed option: {}", objClass, doubleConfirmed);
        }

        Map<String, ColumnField> indexedFields = new HashMap<String, ColumnField>();
        for (ColumnField field : doType.getColumnFields()) {
            if (field.getIndex() != null) {
                indexedFields.put(field.getName(), field);
            }
        }

        if (indexedFields.isEmpty()) {
            return;
        }

        Keyspace keyspace = dbClient.getKeyspace(objClass);
        ColumnFamilyQuery<String, CompositeColumnName> query = keyspace.prepareQuery(doType.getCF());
        OperationResult<Rows<String, CompositeColumnName>> result = query.getAllRows().setRowLimit(dbClient.DEFAULT_PAGE_SIZE).execute();

        int scannedRows = 0;
        long beginTime = System.currentTimeMillis();
        for (Row<String, CompositeColumnName> objRow : result.getResult()) {
            try {
                boolean inactiveObject = false;
                boolean hasInactiveColumn = false;
                scannedRows++;

                Map<String, Column<CompositeColumnName>> distinctColumns = new HashMap<String, Column<CompositeColumnName>>();
                for (Column<CompositeColumnName> column : objRow.getColumns()) {
                	//only check columns with latest value
                	distinctColumns.put(column.getName().getOne(), column);
                    if (column.getName().getOne().equals(DataObject.INACTIVE_FIELD_NAME)) {
                        hasInactiveColumn = true;
                        inactiveObject = column.getBooleanValue();
                    }
                }

                if (!hasInactiveColumn || inactiveObject) {
                    if (!hasInactiveColumn) {
                        _log.warn("Data object with key {} has NO inactive column, don't rebuild index for it.", objRow.getKey());
                    }
                    continue;
                }

                for (Column<CompositeColumnName> column : distinctColumns.values()) {
                    if (!indexedFields.containsKey(column.getName().getOne())) {
                        continue;
                    }

                    // we don't build index if the value is null, refer to ColumnField.
                    if (!column.hasValue()) {
                        continue;
                    }

                    ColumnField indexedField = indexedFields.get(column.getName().getOne());
                    String indexKey = getIndexKey(indexedField, column, objRow);

                    if (indexKey == null) {
                        continue;
                    }

                    boolean isColumnInIndex = isColumnInIndex(keyspace, indexedField.getIndexCF(), indexKey,
                            getIndexColumns(indexedField, column, objRow.getKey()));

                    if (!isColumnInIndex) {
                    	if (doubleConfirmed && (isDataObjectRemoved(doType.getDataObjectClass(), objRow.getKey())
                        		|| !isColumnExists(keyspace, doType.getCF(), column, objRow.getKey()))) {
                            continue;
                        }

                        String dbVersion = findDataCreatedInWhichDBVersion(column.getName().getTimeUUID());
                        checkResult.increaseByVersion(dbVersion);
                        logMessage(String.format(
                                "Inconsistency found Object(%s, id: %s, field: %s) is existing, but the related Index(%s, type: %s, id: %s) is missing. This entry is updated by version %s",
                                indexedField.getDataObjectType().getSimpleName(), objRow.getKey(), indexedField.getName(),
                                indexedField.getIndexCF().getName(), indexedField.getIndex().getClass().getSimpleName(), indexKey, dbVersion),
                                true, toConsole);
                        DbCheckerFileWriter.writeTo(DbCheckerFileWriter.WRITER_REBUILD_INDEX,
                                String.format("id:%s, cfName:%s", objRow.getKey(),
                                        doType.getCF().getName()));
                    }
                }

                if (scannedRows >= THRESHHOLD_FOR_OUTPUT_DEBUG) {
                    _log.info("{} data objects have been check with time {}", scannedRows,
                            DurationFormatUtils.formatDurationHMS(System.currentTimeMillis() - beginTime));
                    scannedRows = 0;
                    beginTime = System.currentTimeMillis();
                }
            } catch (Exception e) {
                _log.warn("exception occurs when checking CF indexes", e);
            }
        }
    }

    public void checkIndexingCF(IndexAndCf indexAndCf, boolean toConsole, CheckResult checkResult) throws ConnectionException {
        checkIndexingCF(indexAndCf, toConsole, checkResult, false);
    }

    public void checkIndexingCF(IndexAndCf indexAndCf, boolean toConsole, CheckResult checkResult, boolean isParallel)
            throws ConnectionException {
        checkIndexingCF(indexAndCf, toConsole, checkResult, false, null);
    }

    /**
     * Scan all the indices and related data object records, to find out
     * the index record is existing but the related data object records is missing.
     *
     * @return number of the corrupted rows in this index CF
     * @throws ConnectionException
     */
    public void checkIndexingCF(IndexAndCf indexAndCf, boolean toConsole, CheckResult checkResult, boolean isParallel, DataObjectType doType) throws ConnectionException {
        initSchemaVersions();
        String indexCFName = indexAndCf.cf.getName();
        Map<String, ColumnFamily<String, CompositeColumnName>> objCfs = getDataObjectCFs();
        _log.info("Start checking the index CF {} with double confirmed option: {}", indexCFName, doubleConfirmed);

        Map<ColumnFamily<String, CompositeColumnName>, Map<String, List<IndexEntry>>> objsToCheck = new HashMap<>();

        ColumnFamilyQuery<String, IndexColumnName> query = indexAndCf.keyspace
                .prepareQuery(indexAndCf.cf);

        OperationResult<Rows<String, IndexColumnName>> result = query.getAllRows()
                .setRowLimit(dbClient.DEFAULT_PAGE_SIZE)
                .withColumnRange(new RangeBuilder().setLimit(0).build())
                .execute();

        int scannedRows = 0;
        long beginTime = System.currentTimeMillis();
        for (Row<String, IndexColumnName> row : result.getResult()) {
            try {
                RowQuery<String, IndexColumnName> rowQuery = indexAndCf.keyspace.prepareQuery(indexAndCf.cf).getKey(row.getKey())
                        .autoPaginate(true)
                        .withColumnRange(new RangeBuilder().setLimit(dbClient.DEFAULT_PAGE_SIZE).build());
                ColumnList<IndexColumnName> columns;

                while (!(columns = rowQuery.execute().getResult()).isEmpty()) {
                    for (Column<IndexColumnName> column : columns) {
                        scannedRows++;
                        ObjectEntry objEntry = extractObjectEntryFromIndex(row.getKey(),
                                column.getName(), indexAndCf.indexType, toConsole);
                        if (objEntry == null) {
                            continue;
                        }

                        if (doType != null && !doType.getDataObjectClass().getSimpleName().equals(objEntry.getClassName())) {
                            continue;
                        }

                        ColumnFamily<String, CompositeColumnName> objCf = objCfs
                                .get(objEntry.getClassName());

                        if (objCf == null) {
                            logMessage(String.format("DataObject does not exist for %s", row.getKey()), true, toConsole);
                            continue;
                        }

                        if (skipCheckCFs.contains(objCf.getName())) {
                            _log.debug("Skip checking CF {} for index CF {}", objCf.getName(), indexAndCf.cf.getName());
                            continue;
                        }

                        Map<String, List<IndexEntry>> objKeysIdxEntryMap = objsToCheck.get(objCf);
                        if (objKeysIdxEntryMap == null) {
                            objKeysIdxEntryMap = new HashMap<>();
                            objsToCheck.put(objCf, objKeysIdxEntryMap);
                        }
                        List<IndexEntry> idxEntries = objKeysIdxEntryMap.get(objEntry.getObjectId());
                        if (idxEntries == null) {
                            idxEntries = new ArrayList<>();
                            objKeysIdxEntryMap.put(objEntry.getObjectId(), idxEntries);
                        }
                        idxEntries.add(new IndexEntry(row.getKey(), column.getName()));
                    }

                    int size = getObjsSize(objsToCheck);
                    if (size >= INDEX_OBJECTS_BATCH_SIZE) {
                        if (isParallel) {
                            processBatchIndexObjectsWithMultipleThreads(indexAndCf, toConsole, objsToCheck, checkResult);
                        } else {
                            processBatchIndexObjects(indexAndCf, toConsole, objsToCheck, checkResult);
                        }
                        objsToCheck = new HashMap<>();
                    }

                    if (scannedRows >= THRESHHOLD_FOR_OUTPUT_DEBUG) {
                        _log.info("{} data objects have been check with time {}", scannedRows,
                                DurationFormatUtils.formatDurationHMS(System.currentTimeMillis() - beginTime));
                        scannedRows = 0;
                        beginTime = System.currentTimeMillis();
                    }
                }
            } catch (Exception e) {
                _log.warn("exception occurs when checking missing data objects", e);
            }
        }

        // Detect whether the DataObject CFs have the records
        if (isParallel) {
            processBatchIndexObjectsWithMultipleThreads(indexAndCf, toConsole, objsToCheck, checkResult);
        } else {
            processBatchIndexObjects(indexAndCf, toConsole, objsToCheck, checkResult);
        }
    }

    private int getObjsSize(Map<ColumnFamily<String, CompositeColumnName>, Map<String, List<IndexEntry>>> objsToCheck) {
        int size = 0;
        for (Map<String, List<IndexEntry>> objMap : objsToCheck.values()) {
            for (List<IndexEntry> objs : objMap.values()) {
                size += objs.size();
            }
        }
        return size;
    }
    
    public void waitForCheckIndexFinihsed(int waitTimeInSeconds) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(waitTimeInSeconds, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            } 
        } catch (Exception e) {
            executor.shutdownNow();
        }
    }

    private void processBatchIndexObjects(IndexAndCf indexAndCf, boolean toConsole,
                                          Map<ColumnFamily<String, CompositeColumnName>, Map<String, List<IndexEntry>>> objsToCheck, CheckResult checkResult) throws ConnectionException {
        for (ColumnFamily<String, CompositeColumnName> objCf : objsToCheck.keySet()) {
            try {
                Map<String, List<IndexEntry>> objKeysIdxEntryMap = objsToCheck.get(objCf);

                _log.info("query {} data object from CF {} for index CF {}", objKeysIdxEntryMap.keySet().size(), objCf.getName(), indexAndCf.cf.getName());
                OperationResult<Rows<String, CompositeColumnName>> objResult = indexAndCf.keyspace
                        .prepareQuery(objCf).getRowSlice(objKeysIdxEntryMap.keySet())
                        .execute();
                for (Row<String, CompositeColumnName> row : objResult.getResult()) {
                    Set<UUID> existingDataColumnUUIDSet = new HashSet<>();
                    for (Column<CompositeColumnName> column : row.getColumns()) {
                        if (column.getName().getTimeUUID() != null) {
                            existingDataColumnUUIDSet.add(column.getName().getTimeUUID());
                        }
                    }

                    List<IndexEntry> idxEntries = objKeysIdxEntryMap.get(row.getKey());
                    for (IndexEntry idxEntry : idxEntries) {
                        if (row.getColumns().isEmpty()
                                || (idxEntry.getColumnName().getTimeUUID() != null && !existingDataColumnUUIDSet.contains(idxEntry
                                .getColumnName().getTimeUUID()))) {
                            //double confirm it is inconsistent data, please see issue COP-27749
                            if (doubleConfirmed && !isIndexExists(indexAndCf.keyspace, indexAndCf.cf, idxEntry.getIndexKey(), idxEntry.getColumnName())) {
                                continue;
                            }

                            String dbVersion = findDataCreatedInWhichDBVersion(idxEntry.getColumnName().getTimeUUID());
                            checkResult.increaseByVersion(dbVersion);
                            if (row.getColumns().isEmpty()) {
                                logMessage(String.format("Inconsistency found: Index(%s, type: %s, id: %s, column: %s) is existing "
                                                + "but the related object record(%s, id: %s) is missing. This entry is updated by version %s",
                                        indexAndCf.cf.getName(), indexAndCf.indexType.getSimpleName(),
                                        idxEntry.getIndexKey(), idxEntry.getColumnName(),
                                        objCf.getName(), row.getKey(), dbVersion), true, toConsole);
                            } else {
                                logMessage(String.format("Inconsistency found: Index(%s, type: %s, id: %s, column: %s) is existing, "
                                                + "but the related object record(%s, id: %s) has not data column can match this index. This entry is updated by version %s",
                                        indexAndCf.cf.getName(), indexAndCf.indexType.getSimpleName(),
                                        idxEntry.getIndexKey(), idxEntry.getColumnName(),
                                        objCf.getName(), row.getKey(), dbVersion), true, toConsole);
                            }
                            UUID timeUUID = idxEntry.getColumnName().getTimeUUID();
                            DbCheckerFileWriter.writeTo(indexAndCf.keyspace.getKeyspaceName(),
                                    generateCleanIndexCQL(indexAndCf, idxEntry, timeUUID, idxEntry.getColumnName()));
                        }
                    }
                }
            } catch (Exception e) {
                _log.warn("exception occurs when processing batch index objects", e);
            }
        }
    }

    protected String generateCleanIndexCQL(IndexAndCf indexAndCf, IndexEntry idxEntry, UUID timeUUID, CompositeIndexColumnName compositeIndexColumnName) {
        if (compositeIndexColumnName instanceof ClassNameTimeSeriesIndexColumnName ||
                compositeIndexColumnName instanceof TimeSeriesIndexColumnName) {
            return String.format(timeUUID != null ? DELETE_ORDER_INDEX_CQL : DELETE_ORDER_INDEX_CQL_WITHOUT_UUID,
                    indexAndCf.cf.getName(), idxEntry.getIndexKey(), idxEntry.getColumnName().getOne(),
                    handleNullValue(idxEntry.getColumnName().getTwo()),
                    handleNullValue(idxEntry.getColumnName().getThree()),
                    handleNullValue(idxEntry.getColumnName().getFour()),
                    timeUUID);
        } else {
            return String.format(timeUUID != null ? DELETE_INDEX_CQL : DELETE_INDEX_CQL_WITHOUT_UUID,
                indexAndCf.cf.getName(), idxEntry.getIndexKey(), idxEntry.getColumnName().getOne(),
                handleNullValue(idxEntry.getColumnName().getTwo()),
                handleNullValue(idxEntry.getColumnName().getThree()),
                handleNullValue(idxEntry.getColumnName().getFour()),
                timeUUID);
        }
    }
    
    /*
     * We need to process index objects in batch to avoid occupy too many memory
     * */
    private void processBatchIndexObjectsWithMultipleThreads(IndexAndCf indexAndCf, boolean toConsole,
            Map<ColumnFamily<String, CompositeColumnName>, Map<String, List<IndexEntry>>> objsToCheck, CheckResult checkResult) throws ConnectionException {
        //if waiting queue is full, wait a few seconds to avoid reject exception 
        while (executor.getQueue().size() >= THREAD_POOL_QUEUE_SIZE) {
            try {
                Thread.sleep(WAITING_TIME_FOR_QUEUE_FULL_MS);
            } catch (InterruptedException e) {
                //ignore
            }
        }
        
        executor.submit(new Runnable() {

            @Override
            public void run() {
                try {
                    processBatchIndexObjects(indexAndCf, toConsole, objsToCheck, checkResult);
                } catch (ConnectionException e) {
                    _log.error("failed to check index:", e);
                }
            }
            
        });
    }

    public Map<String, IndexAndCf> getAllIndices() {
        // Map<Index_CF_Name, <DbIndex, ColumnFamily, Map<Class_Name, object-CF_Name>>>
        Map<String, IndexAndCf> allIdxCfs = new TreeMap<>();
        for (DataObjectType objType : TypeMap.getAllDoTypes()) {
            Map<String, IndexAndCf> idxCfs = getIndicesOfCF(objType);
            allIdxCfs.putAll(idxCfs);
        }

        return allIdxCfs;
    }

    public Map<String, IndexAndCf> getIndicesOfCF(DataObjectType objType) {
        Map<String, IndexAndCf> idxCfs = new TreeMap<>();
        Keyspace keyspace = dbClient.getKeyspace(objType.getDataObjectClass());
        for (ColumnField field : objType.getColumnFields()) {
            DbIndex index = field.getIndex();
            if (index == null) {
                continue;
            }

            IndexAndCf indexAndCf = new IndexAndCf(index.getClass(), field.getIndexCF(), keyspace);
            String key = indexAndCf.generateKey();
            IndexAndCf idxAndCf = idxCfs.get(key);
            if (idxAndCf == null) {
                idxAndCf = new IndexAndCf(index.getClass(), field.getIndexCF(), keyspace);
                idxCfs.put(key, idxAndCf);
            }
        }
        return idxCfs;
    }

    public Map<String, ColumnFamily<String, CompositeColumnName>> getDataObjectCFs() {
        Map<String, ColumnFamily<String, CompositeColumnName>> objCfs = new TreeMap<>();
        for (DataObjectType objType : TypeMap.getAllDoTypes()) {
            String simpleClassName = objType.getDataObjectClass().getSimpleName();
            ColumnFamily<String, CompositeColumnName> objCf = objCfs.get(simpleClassName);
            if (objCf == null) {
                objCfs.put(simpleClassName, objType.getCF());
            }
        }

        return objCfs;
    }

    void logMessage(String msg, boolean isError, boolean toConsole) {
        if (StringUtils.isEmpty(msg)) {
            return;
        }
        if (isError) {
            _log.error(msg);
            if (toConsole) {
                System.err.println(msg);
            }
            return;
        }

        _log.info(msg);
        if (toConsole) {
            System.out.println(msg);
        }
    }

    /*
     * This class records the Index Data's ColumnFamily and
     * the related DbIndex type and it belongs to which Keyspace.
     */
    public static class IndexAndCf<T extends CompositeIndexColumnName> implements Comparable {
        private ColumnFamily<String, T> cf;
        private Class<? extends DbIndex> indexType;
        private Keyspace keyspace;

        public IndexAndCf(Class<? extends DbIndex> indexType,
                ColumnFamily<String, T> cf, Keyspace keyspace) {
            this.indexType = indexType;
            this.cf = cf;
            this.keyspace = keyspace;
        }

        @Override
        public String toString() {
            return generateKey();
        }

        String generateKey() {
            StringBuffer buffer = new StringBuffer();
            buffer.append(keyspace.getKeyspaceName()).append("/")
                    .append(indexType.getSimpleName()).append("/")
                    .append(cf.getName());
            return buffer.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof IndexAndCf)) {
                return false;
            }

            if (this == obj) {
                return true;
            }

            IndexAndCf that = (IndexAndCf) obj;
            if (cf != null ? !cf.equals(that.cf) : that.cf != null) {
                return false;
            }
            if (indexType != null ? !indexType.equals(that.indexType)
                    : that.indexType != null) {
                return false;
            }
            if (keyspace != null ? !keyspace.equals(that.keyspace)
                    : that.keyspace != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = cf != null ? cf.hashCode() : 0;
            result = 31 * result + (indexType != null ? indexType.hashCode() : 0);
            result = 31 * result + (keyspace != null ? keyspace.hashCode() : 0);
            return result;
        }

        @Override
        public int compareTo(Object object) {
            IndexAndCf other = (IndexAndCf) object;
            return this.generateKey().compareTo(other.generateKey());
        }
    }

    public class IndexEntry {
        private String indexKey;
        private CompositeIndexColumnName columnName;

        public IndexEntry(String indexKey, CompositeIndexColumnName columnName) {
            this.indexKey = indexKey;
            this.columnName = columnName;
        }

        public String getIndexKey() {
            return indexKey;
        }

        public CompositeIndexColumnName getColumnName() {
            return columnName;
        }

    }

    private ObjectEntry extractObjectEntryFromIndex(String indexKey,
            CompositeIndexColumnName name, Class<? extends DbIndex> type, boolean toConsole) {
        // The className of a data object CF in a index record
        String className;
        // The id of the data object record in a index record
        String objectId;
        if (type.equals(AltIdDbIndex.class)) {
            objectId = name.getTwo();
            className = name.getOne();
        } else if (type.equals(RelationDbIndex.class)) {
            objectId = name.getTwo();
            className = name.getOne();
        } else if (type.equals(NamedRelationDbIndex.class)) {
            objectId = name.getFour();
            className = name.getOne();
        } else if (type.equals(DecommissionedDbIndex.class)) {
            objectId = name.getTwo();
            className = indexKey;
        } else if (type.equals(PermissionsDbIndex.class)) {
            objectId = name.getTwo();
            className = name.getOne();
        } else if (type.equals(PrefixDbIndex.class)) {
            objectId = name.getFour();
            className = name.getOne();
        } else if (type.equals(ScopedLabelDbIndex.class)) {
            objectId = name.getFour();
            className = name.getOne();
        } else if (type.equals(AggregateDbIndex.class)) {
            objectId = name.getTwo();
            int firstColon = indexKey.indexOf(':');
            className = firstColon == -1 ? indexKey : indexKey.substring(0, firstColon);
        } else if (type.equals(ClassNameTimeSeriesDBIndex.class)) {
            objectId = name.getThree();
            className = name.getOne();
        } else if (type.equals(TimeSeriesDbIndex.class)) {
            objectId = name.getThree();
            className = name.getOne();
        } else {
            String msg = String.format("Unsupported index type %s.", type);
            logMessage(msg, false, toConsole);
            return null;
        }

        return new ObjectEntry(className, objectId);
    }

    public static class ObjectEntry {
        private String className;
        private String objectId;

        public ObjectEntry(String className, String objectId) {
            this.className = className;
            this.objectId = objectId;
        }

        public String getClassName() {
            return className;
        }

        public String getObjectId() {
            return objectId;
        }

        @Override
        public String toString() {
            StringBuffer buffer = new StringBuffer();
            buffer.append("ObjectEntry ClassName: ").append(className).append(" ObjectId: ").append(objectId);
            return buffer.toString();
        }
    }

    private boolean isColumnInIndex(Keyspace ks, ColumnFamily<String, CompositeIndexColumnName> indexCf, String indexKey, Object[] indexColumns)
            throws ConnectionException {
        StringBuilder cql = new StringBuilder("select * from ");
        cql.append("\"").append(indexCf.getName())
        .append("\" where key=? ");

        for (int i = 0; i < indexColumns.length; i++) {
        	cql.append(" and column").append(i + 1).append("=?");
        }

		PreparedCqlQuery preparedStatement = ks.prepareQuery(indexCf).withCql(cql.toString()).asPreparedStatement();

		preparedStatement.withStringValue(indexKey);
		for (int i = 0; i < indexColumns.length; i++) {
			if (indexColumns[i] instanceof String) {
				preparedStatement.withStringValue((String) indexColumns[i]);
			} else if (indexColumns[i] instanceof Long) {
				preparedStatement.withLongValue((Long) indexColumns[i]);
			} else {
				//suppose only String or Long, if un-supported type detect, just return true
				return true;
			}
		}
		
		ThriftCqlResultImpl cqlResult = (ThriftCqlResultImpl)preparedStatement.execute().getResult();
		if (cqlResult == null || cqlResult.getRows() == null) {
			return false;
		} else
			return cqlResult.getRows().size() > 0;
    }
    
    public <T extends CompositeIndexColumnName> boolean isIndexExists(Keyspace ks, ColumnFamily<String, T> indexCf, String indexKey, T column) throws ConnectionException {
        try {
            ks.prepareQuery(indexCf).getKey(indexKey)
                    .getColumn(column)
                    .execute().getResult();
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }
    
    public boolean isColumnExists(Keyspace ks, ColumnFamily<String, CompositeColumnName> cf, Column<CompositeColumnName> column, String key) throws ConnectionException {
    	try {
            ks.prepareQuery(cf).getKey(key)
                    .getColumn(column.getName())
                    .execute().getResult();
            return true;
        } catch (NotFoundException e) {
        	_log.warn("index not exist when double check, key: {}, column: {}", key, column);
            return false;
        }
    }

    public static String getIndexKey(ColumnField field, Column<CompositeColumnName> column, Row<String, CompositeColumnName> objRow) {
        String indexKey = null;
        DbIndex dbIndex = field.getIndex();
        boolean indexByKey = field.isIndexByKey();
        if (dbIndex instanceof AltIdDbIndex) {
            indexKey = indexByKey ? column.getName().getTwo() : column.getStringValue();
        } else if (dbIndex instanceof RelationDbIndex) {
            indexKey = indexByKey ? column.getName().getTwo() : column.getStringValue();
        } else if (dbIndex instanceof NamedRelationDbIndex) {
            indexKey = NamedURI.fromString(column.getStringValue()).getURI().toString();
        } else if (dbIndex instanceof DecommissionedDbIndex) {
            indexKey = field.getDataObjectType().getSimpleName();
        } else if (dbIndex instanceof PermissionsDbIndex) {
            indexKey = column.getName().getTwo();
        } else if (dbIndex instanceof PrefixDbIndex) {
            String columnValue = column.getStringValue();
            int columnMinChars = ((PrefixDbIndex) dbIndex).getMinPrefixChars();

            if(columnValue.length() < columnMinChars){
                indexKey = null;
            } else {
                indexKey = field.getPrefixIndexRowKey(columnValue);
            }

        } else if (dbIndex instanceof ScopedLabelDbIndex) {
            indexKey = field.getPrefixIndexRowKey(ScopedLabel.fromString(column.getStringValue()));
        } else if (dbIndex instanceof ClassNameTimeSeriesDBIndex) {
            indexKey = column.getStringValue();
        } else if (dbIndex instanceof TimeSeriesDbIndex) {
            if (field.getDataObjectType().equals(Order.class)) {
                Order order = new Order();
                DataObjectType doType = TypeMap.getDoType(Order.class);
                doType.deserializeColumns(order, objRow, Lists.newArrayList(doType.getColumnField("tenant")), true);
                indexKey = order.getTenant();
            }
        } else if (dbIndex instanceof AggregateDbIndex) {
            // Not support this index type yet.
        } else {
            String msg = String.format("Unsupported index type %s.", dbIndex.getClass());
            _log.warn(msg);
        }

        return indexKey;
    }

    public static Object[] getIndexColumns(ColumnField field, Column<CompositeColumnName> column, String rowKey) {
        Object[] indexColumns = null;
        DbIndex dbIndex = field.getIndex();

        if (dbIndex instanceof AggregateDbIndex) {
            // Not support this index type yet.
            return indexColumns;
        }

        if (dbIndex instanceof NamedRelationDbIndex) {
            indexColumns = new String[4];
            indexColumns[0] = field.getDataObjectType().getSimpleName();
            NamedURI namedURI = NamedURI.fromString(column.getStringValue());
            String name = namedURI.getName();
            indexColumns[1] = name.toLowerCase();
            indexColumns[2] = name;
            indexColumns[3] = rowKey;

        } else if (dbIndex instanceof PrefixDbIndex) {
            indexColumns = new String[4];
            indexColumns[0] = field.getDataObjectType().getSimpleName();
            indexColumns[1] = column.getStringValue().toLowerCase();
            indexColumns[2] = column.getStringValue();
            indexColumns[3] = rowKey;

        } else if (dbIndex instanceof ScopedLabelDbIndex) {
            indexColumns = new String[4];
            indexColumns[0] = field.getDataObjectType().getSimpleName();
            ScopedLabel label = ScopedLabel.fromString(column.getStringValue());
            indexColumns[1] = label.getLabel().toLowerCase();
            indexColumns[2] = label.getLabel();
            indexColumns[3] = rowKey;

        } else if (dbIndex instanceof DecommissionedDbIndex) {
            indexColumns = new String[2];
            Boolean val = column.getBooleanValue();
            indexColumns[0] = val.toString();
            indexColumns[1] = rowKey;
        } else if (dbIndex instanceof ClassNameTimeSeriesDBIndex || dbIndex instanceof TimeSeriesDbIndex) {
            indexColumns = new Object[3];
            indexColumns[0] = field.getDataObjectType().getSimpleName();
            indexColumns[1] = TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID());
            indexColumns[2] = rowKey;
        } else {
            // For AltIdDbIndex, RelationDbIndex, PermissionsDbIndex
            indexColumns = new String[2];
            indexColumns[0] = field.getDataObjectType().getSimpleName();
            indexColumns[1] = rowKey;
        }
        return indexColumns;
    }

    private String handleNullValue(String columnValue) {
        return columnValue == null ? "" : columnValue;
    }

    public DbClientImpl getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClientImpl dbClient) {
        this.dbClient = dbClient;
    }
    
    protected boolean isDataObjectRemoved(Class<? extends DataObject> clazz, String key) {
    	List<? extends DataObject> dataObjects = dbClient.queryObjectField(clazz, "inactive", Lists.newArrayList(URI.create(key)));
    	return dataObjects == null || dataObjects.isEmpty() || dataObjects.get(0).getInactive();
    }
    
    private boolean isValidDataObjectKey(URI uri, final Class<? extends DataObject> type) {
    	return uri != null && URIUtil.isValid(uri) && URIUtil.isType(uri, type);
    }
    
    protected void initSchemaVersions() {
        if (schemaVersionsTime == null) {
            schemaVersionsTime = querySchemaVersions();
        }
    }

    protected Map<Long, String> querySchemaVersions() {
        Map<Long, String> result = new TreeMap<Long, String>();
        ColumnFamily<String, String> CF_STANDARD1 =
                new ColumnFamily<String, String>("SchemaRecord",
                        StringSerializer.get(), StringSerializer.get(), StringSerializer.get());
        try {
            OperationResult<CqlResult<String, String>> queryResult = dbClient.getLocalContext().getKeyspace().prepareQuery(CF_STANDARD1)
                    .withCql(CQL_QUERY_SCHEMA_VERSION_TIMESTAMP)
                    .execute();
            for (Row<String, String> row : queryResult.getResult().getRows()) {
                result.put(row.getColumns().getColumnByIndex(1).getLongValue(), row.getColumns().getColumnByIndex(0).getStringValue());
            }
        } catch (ConnectionException e) {
            _log.error("Failed to query schema versions", e);
        }
        
        return result;
    }
    
    public String findDataCreatedInWhichDBVersion(UUID timeUUID) {
        
        long createTime = 0;
        try {
            createTime = TimeUUIDUtils.getMicrosTimeFromUUID(timeUUID);
        } catch (Exception e) {
            //ignore
        }
        
        return findDataCreatedInWhichDBVersion(createTime);
    }
    
    public String findDataCreatedInWhichDBVersion(long createTime) {
        //small data set, no need to binary search
        long selectKey = 0;
        for (Entry<Long, String> entry : schemaVersionsTime.entrySet()) {
            if (createTime >= entry.getKey()) {
                selectKey = entry.getKey();
            }
        }
        
        return selectKey == 0 ? "Unknown" : schemaVersionsTime.get(selectKey);
    }
    
    public ThreadPoolExecutor getExecutor() {
        return executor;
    }

    public void setDoubleConfirmed(boolean doubleConfirmed) {
        this.doubleConfirmed = doubleConfirmed;
    }

    public static class CheckResult {
        //The number of the corrupted rows
        private AtomicInteger total = new AtomicInteger();
        private Map<String, Integer> countOfVersion = Collections.synchronizedMap(new TreeMap<String, Integer>());
        
        public int getTotal() {
            return total.get();
        }

        public Map<String, Integer> getCountOfVersion() {
            return countOfVersion;
        }

        public void increaseByVersion(String version) {
            if (!countOfVersion.containsKey(version)) {
                countOfVersion.put(version, 0);
            }
            
            countOfVersion.put(version, countOfVersion.get(version) + 1);
            this.total.getAndIncrement();
        }

        @Override
        public String toString() {
            if (0 == getTotal()) {
                return "\nNo corrupted rows found.";
            }
            StringBuilder builder = new StringBuilder();
            builder.append("\nCorrupted rows by version: ");
            int index = 1;
            int max = countOfVersion.size();
            for (Entry<String, Integer> entry : countOfVersion.entrySet()) {
                builder.append(entry.getKey()).append("(").append(entry.getValue()).append(")");
                if (index++ < max) {
                    builder.append(", ");
                }
            }
            return builder.toString();
        }
    }
}
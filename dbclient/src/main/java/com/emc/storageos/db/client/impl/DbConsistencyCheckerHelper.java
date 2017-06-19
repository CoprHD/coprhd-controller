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

import org.apache.cassandra.serializers.BooleanSerializer;
import org.apache.cassandra.serializers.UTF8Serializer;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.utils.UUIDs;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.PasswordHistory;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.uimodels.ExecutionLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.exceptions.DatabaseException;

import com.google.common.collect.Lists;

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
            String queryString = String.format("select key from \"%s\"", doType.getCF().getName());
            SimpleStatement queryStatement = new SimpleStatement(queryString);
            queryStatement.setFetchSize(dbClient.DEFAULT_PAGE_SIZE);
            
            ResultSet resultSet = dbClient.getSession(doType.getDataObjectClass()).execute(queryStatement);
            
            for (Row row : resultSet) {
                String key = row.getString(0);
                try {
                    if (!isValidDataObjectKey(URI.create(key), dataObjectClass)) {
                        dirtyCount++;
                        logMessage(String.format("Inconsistency found: Row key '%s' failed to convert to URI in CF %s",
                                key, dataObjectClass.getName()), true, toConsole);
                    }
                } catch (Exception ex) {
                    dirtyCount++;
                    logMessage(String.format("Inconsistency found: Row key '%s' failed to convert to URI in CF %s with exception %s",
                    		key, doType.getDataObjectClass()
                                    .getName(),
                            ex.getMessage()), true, toConsole);
                }
            }

        } catch (DriverException e) {
            throw DatabaseException.retryables.operationFailed(e);
        }

        return dirtyCount;
    }

    /**
     * Scan all the data object records, to find out the object record is existing but the related index is missing.
     *
     * @param doType
     * @param toConsole whether print out in the console
     * @return the number of corrupted data
     */
    public void checkCFIndices(DataObjectType doType, boolean toConsole, CheckResult checkResult) throws DriverException {
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

        String queryString = String.format("select distinct key from \"%s\"", doType.getCF().getName());
        SimpleStatement queryStatement = new SimpleStatement(queryString);
        queryStatement.setFetchSize(dbClient.DEFAULT_PAGE_SIZE);
        ResultSet resultSet = dbClient.getSession(doType.getDataObjectClass()).execute(queryStatement);
        
        List<URI> ids = new ArrayList<>();
        for (Row row : resultSet) {
        	ids.add(URI.create(row.getString(0)));
        	if (ids.size() >= dbClient.DEFAULT_PAGE_SIZE || !resultSet.isExhausted()) {
        		continue;
        	}
                        
			Map<String, List<CompositeColumnName>> result = dbClient.queryRowsWithAllColumns(
							dbClient.getDbClientContext(doType
									.getDataObjectClass()), ids, doType
									.getCF().getName());
			ids = new ArrayList<>();
        	for (String rowKey : result.keySet()) {
                List<CompositeColumnName> columns = result.get(rowKey);
                boolean inactiveObject = false;
                boolean hasInactiveColumn = false;
                for (CompositeColumnName column : columns) {
                    if (column.getOne().equals(DataObject.INACTIVE_FIELD_NAME)) {
                    	hasInactiveColumn = true;
                    	inactiveObject = column.getBooleanValue();
                    	break;
                    }

                }
                
                if (!hasInactiveColumn || inactiveObject) {
                	if (!hasInactiveColumn) {
                		_log.warn("Data object with key {} has NO inactive column, don't rebuild index for it.", rowKey);
                	}
                	continue;
                }
                
                for (CompositeColumnName column : columns) {
                    // we don't build index if the value is null, refer to ColumnField.
                    if (column == null) {
                        continue;
                    }

                    if (!indexedFields.containsKey(column.getOne())) {
                        continue;
                    }
                    
                    ColumnField indexedField = indexedFields.get(column.getOne());
                    String indexKey = getIndexKey(indexedField, column, columns);
                    if (indexKey == null || indexKey.isEmpty()) {
                        logMessage(String.format("indexKey is null or empty for field: %s  row key: %s", indexedField.getName(), rowKey), true, false);
                        continue;
                    }
                    
                    boolean isColumnInIndex = isColumnInIndex(doType, indexedField.getIndexCF().getName(), indexKey,
                            getIndexColumns(indexedField, column, rowKey));
                    if (!isColumnInIndex) {
                    	if (doubleConfirmed && isDataObjectRemoved(doType.getDataObjectClass(), rowKey)) {
                            continue;
                        }
                    	
                    	String dbVersion = findDataCreatedInWhichDBVersion(column.getTimeUUID());
                        checkResult.increaseByVersion(dbVersion);
                    	
                        logMessage(String.format(
                                "Inconsistency found Object(%s, id: %s, field: %s) is existing, but the related Index(%s, type: %s, id: %s) is missing. This entry is updated by version %s",
                                indexedField.getDataObjectType().getSimpleName(), rowKey, indexedField.getName(),
                                indexedField.getIndexCF().getName(), indexedField.getIndex().getClass().getSimpleName(), indexKey, dbVersion),
                                true, toConsole);
                        DbCheckerFileWriter.writeTo(DbCheckerFileWriter.WRITER_REBUILD_INDEX,
                                String.format("id:%s, cfName:%s", rowKey,
                                		doType.getCF().getName()));
                    }
                }
            }
        }
    }

    public void checkIndexingCF(IndexAndCf indexAndCf, boolean toConsole, CheckResult checkResult) throws DriverException {
        checkIndexingCF(indexAndCf, toConsole, checkResult, false);
    }

    /**
     * Scan all the indices and related data object records, to find out
     * the index record is existing but the related data object records is missing.
     *
     * @return number of the corrupted rows in this index CF
     */
    public void checkIndexingCF(IndexAndCf indexAndCf, boolean toConsole, CheckResult checkResult, boolean isParallel) throws DriverException {
        String indexCFName = indexAndCf.cf;
        Map<String, String> objCfs = getDataObjectCFs();
        _log.info("Start checking the index CF {}", indexCFName);

        Map<String, Map<String, List<IndexEntry>>> objsToCheck = new HashMap<>();
        
        SimpleStatement queryStatement = new SimpleStatement(String.format("select * from \"%s\"", indexAndCf.cf));
        queryStatement.setFetchSize(dbClient.DEFAULT_PAGE_SIZE);
        
        ResultSet resultSet = indexAndCf.dbClientContext.getSession().execute(queryStatement);

		int scannedRows = 0;
		long beginTime = System.currentTimeMillis();
        for (Row row : resultSet) {
        	scannedRows++;
        	String key = row.getString(0);
            
        	CompositeIndexColumnName indexColumnName = null;
        	
        	if (ClassNameTimeSeriesDBIndex.class.isAssignableFrom(indexAndCf.indexType)) {
        		indexColumnName = new ClassNameTimeSeriesIndexColumnName(row.getLong(2), 
        				row.getString(1),
                        row.getString(3),
                        row.getString(4),
                        row.getUUID(5));
        	} else if (TimeSeriesDbIndex.class.isAssignableFrom(indexAndCf.indexType)) {
        		indexColumnName = new TimeSeriesIndexColumnName(row.getString(1),
        				row.getLong(2),
                        row.getString(3),
                        row.getString(4),
                        row.getUUID(5));
        	} else {
        		indexColumnName = new IndexColumnName(row.getString(1), 
                    row.getString(2), 
                    row.getString(3),
                    row.getString(4),
                    row.getUUID(5),
                    row.getBytes(6));
        	}
            
            ObjectEntry objEntry = extractObjectEntryFromIndex(key,
                    indexColumnName, indexAndCf.indexType, toConsole);
            if (objEntry == null) {
                continue;
            }
            
            String objCfName = objCfs.get(objEntry.getClassName());
            if (objCfName == null) {
                logMessage(String.format("DataObject does not exist for %s", key), true, toConsole);
                continue;
            }
            
            if (skipCheckCFs.contains(objCfName)) {
                _log.debug("Skip checking CF {} for index CF {}", objCfName, indexAndCf.cf);
                continue;
            }

            Map<String, List<IndexEntry>> objKeysIdxEntryMap = objsToCheck.get(objCfName);
            if (objKeysIdxEntryMap == null) {
                objKeysIdxEntryMap = new HashMap<>();
                objsToCheck.put(objCfName, objKeysIdxEntryMap);
            }
            List<IndexEntry> idxEntries = objKeysIdxEntryMap.get(objEntry.getObjectId());
            if (idxEntries == null) {
                idxEntries = new ArrayList<>();
                objKeysIdxEntryMap.put(objEntry.getObjectId(), idxEntries);
            }
            idxEntries.add(new IndexEntry(key, indexColumnName));
            
            
            if (getObjsSize(objsToCheck) >= INDEX_OBJECTS_BATCH_SIZE ) {
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

        // Detect whether the DataObject CFs have the records
        if (isParallel) {
            processBatchIndexObjectsWithMultipleThreads(indexAndCf, toConsole, objsToCheck, checkResult);
        } else {
            processBatchIndexObjects(indexAndCf, toConsole, objsToCheck, checkResult);
        }
    }

    private int getObjsSize(Map<String, Map<String, List<IndexEntry>>> objsToCheck) {
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

    /*
     * We need to process index objects in batch to avoid occupy too many memory
     * */
    private void processBatchIndexObjects(IndexAndCf indexAndCf, boolean toConsole,
            Map<String, Map<String, List<IndexEntry>>> objsToCheck, CheckResult checkResult) {
        for (String objCf : objsToCheck.keySet()) {
            Map<String, List<IndexEntry>> objKeysIdxEntryMap = objsToCheck.get(objCf);
            
            String queryString = String.format("select * from \"%s\" where key in ?", objCf);
            PreparedStatement queryStatement = indexAndCf.dbClientContext.getPreparedStatement(queryString);
            
            ResultSet resultSet = indexAndCf.dbClientContext.getSession().execute(queryStatement.bind(new ArrayList<String>(objKeysIdxEntryMap.keySet())));
            Map<String, List<CompositeColumnName>> rowColumnMap = dbClient.toColumnMap(resultSet);
            
            for (Entry<String, List<IndexEntry>> entry : objKeysIdxEntryMap.entrySet()) {
            	
            	Set<UUID> existingDataColumnUUIDSet = new HashSet<>();
            	if (rowColumnMap.containsKey(entry.getKey())) {
	                for (CompositeColumnName column : rowColumnMap.get(entry.getKey())) {
	                    if (column.getTimeUUID() != null) {
	                        existingDataColumnUUIDSet.add(column.getTimeUUID());
	                    }
	                }
            	}
            	
                for (IndexEntry idxEntry : entry.getValue()) {
                    if (!rowColumnMap.containsKey(entry.getKey())
                            || (idxEntry.getColumnName().getTimeUUID() != null && !existingDataColumnUUIDSet.contains(idxEntry
                                    .getColumnName().getTimeUUID()))) {
                        //double confirm it is inconsistent data, please see issue COP-27749
                        if (doubleConfirmed && !isIndexExists(indexAndCf.dbClientContext, indexAndCf.cf, idxEntry.getIndexKey(), idxEntry.getColumnName())) {
                            continue;
                        }
                        
                        String dbVersion = findDataCreatedInWhichDBVersion(idxEntry.getColumnName().getTimeUUID());
                        checkResult.increaseByVersion(dbVersion);
                        if (rowColumnMap.containsKey(entry.getKey())) {
                            logMessage(String.format("Inconsistency found: Index(%s, type: %s, id: %s, column: %s) is existing "
                                + "but the related object record(%s, id: %s) is missing. This entry is updated by version %s",
                                indexAndCf.cf, indexAndCf.indexType.getSimpleName(),
                                idxEntry.getIndexKey(), idxEntry.getColumnName(),
                                objCf, entry.getKey(), dbVersion), true, toConsole);
                        } else {
                            logMessage(String.format("Inconsistency found: Index(%s, type: %s, id: %s, column: %s) is existing, "
                                    + "but the related object record(%s, id: %s) has not data column can match this index. This entry is updated by version %s",
                                    indexAndCf.cf, indexAndCf.indexType.getSimpleName(),
                                    idxEntry.getIndexKey(), idxEntry.getColumnName(),
                                    objCf, entry.getKey(), dbVersion), true, toConsole);
                        }
                        UUID timeUUID = idxEntry.getColumnName().getTimeUUID();
                        DbCheckerFileWriter.writeTo(indexAndCf.dbClientContext.getKeyspaceName(),
                                generateCleanIndexCQL(indexAndCf, idxEntry, timeUUID, idxEntry.getColumnName()));
                    }
                }
            }
        }
    }

    protected String generateCleanIndexCQL(IndexAndCf indexAndCf, IndexEntry idxEntry, UUID timeUUID, CompositeIndexColumnName compositeIndexColumnName) {
        if (compositeIndexColumnName instanceof ClassNameTimeSeriesIndexColumnName ||
                compositeIndexColumnName instanceof TimeSeriesIndexColumnName) {
            return String.format(timeUUID != null ? DELETE_ORDER_INDEX_CQL : DELETE_ORDER_INDEX_CQL_WITHOUT_UUID,
                    indexAndCf.cf, idxEntry.getIndexKey(), idxEntry.getColumnName().getOne(),
                    handleNullValue(idxEntry.getColumnName().getTwo()),
                    handleNullValue(idxEntry.getColumnName().getThree()),
                    handleNullValue(idxEntry.getColumnName().getFour()),
                    timeUUID);
        } else {
            return String.format(timeUUID != null ? DELETE_INDEX_CQL : DELETE_INDEX_CQL_WITHOUT_UUID,
                indexAndCf.cf, idxEntry.getIndexKey(), idxEntry.getColumnName().getOne(),
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
            Map<String, Map<String, List<IndexEntry>>> objsToCheck, CheckResult checkResult) throws DriverException {
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
                } catch (DriverException e) {
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
        DbClientContext dbClientContext = dbClient.getDbClientContext(objType.getDataObjectClass());
        for (ColumnField field : objType.getColumnFields()) {
            DbIndex index = field.getIndex();
            if (index == null) {
                continue;
            }

            IndexAndCf indexAndCf = new IndexAndCf(index.getClass(), field.getIndexCF().getName(), dbClientContext);
            String key = indexAndCf.generateKey();
            IndexAndCf idxAndCf = idxCfs.get(key);
            if (idxAndCf == null) {
                idxAndCf = new IndexAndCf(index.getClass(), field.getIndexCF().getName(), dbClientContext);
                idxCfs.put(key, idxAndCf);
            }
        }
        return idxCfs;
    }

    public Map<String, String> getDataObjectCFs() {
        Map<String, String> objCfs = new TreeMap<>();
        for (DataObjectType objType : TypeMap.getAllDoTypes()) {
            String simpleClassName = objType.getDataObjectClass().getSimpleName();
            String objCf = objCfs.get(simpleClassName);
            if (objCf == null) {
                objCfs.put(simpleClassName, objType.getCF().getName());
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
    public static class IndexAndCf implements Comparable {
        private String cf;
        private Class<? extends DbIndex> indexType;
        private DbClientContext dbClientContext;

        public IndexAndCf(Class<? extends DbIndex> indexType,
                String cf, DbClientContext dbClientContext) {
            this.indexType = indexType;
            this.cf = cf;
            this.dbClientContext = dbClientContext;
        }

        @Override
        public String toString() {
            return generateKey();
        }

        String generateKey() {
            StringBuffer buffer = new StringBuffer();
            buffer.append(dbClientContext.getKeyspaceName()).append("/")
                    .append(indexType.getSimpleName()).append("/")
                    .append(cf);
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
            if (dbClientContext != null ? !dbClientContext.equals(that.dbClientContext)
                    : that.dbClientContext != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = cf != null ? cf.hashCode() : 0;
            result = 31 * result + (indexType != null ? indexType.hashCode() : 0);
            result = 31 * result + (dbClientContext != null ? dbClientContext.hashCode() : 0);
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

    private boolean isColumnInIndex(DataObjectType doType, String indexCfName, String indexKey, Object[] indexColumns) {
    	DbClientContext dbClientContext = dbClient.getDbClientContext(doType.getDataObjectClass());
    	StringBuilder queryString = new StringBuilder("select * from \"");
    	List<Object> parameters = new ArrayList<Object>();
    	
    	queryString.append(indexCfName);
    	queryString.append("\" where key=?");
    	parameters.add(indexKey);
    	
        for (int i = 0; i < indexColumns.length; i++) {
            queryString.append(" and column").append(i+1).append("=?");
            parameters.add(indexColumns[i]);
        }

        PreparedStatement preparedStatement = dbClientContext.getPreparedStatement(queryString.toString());
        _log.info("queryString: {}", queryString.toString());
        
        ResultSet resultSet = dbClient.getSession(doType.getDataObjectClass()).execute(preparedStatement.bind(parameters.toArray()));
        return resultSet.one() != null;
    }

    public static String getIndexKey(ColumnField field, CompositeColumnName column, List<CompositeColumnName> objRow) {
        String indexKey = null;
        DbIndex dbIndex = field.getIndex();
        boolean indexByKey = field.isIndexByKey();
        if (dbIndex instanceof AltIdDbIndex) {
            indexKey = indexByKey ? column.getTwo() : UTF8Serializer.instance.deserialize(column.getValue());
        } else if (dbIndex instanceof RelationDbIndex) {
            indexKey = indexByKey ? column.getTwo() : UTF8Serializer.instance.deserialize(column.getValue());
        } else if (dbIndex instanceof NamedRelationDbIndex) {
            indexKey = NamedURI.fromString(UTF8Serializer.instance.deserialize(column.getValue())).getURI().toString();
        } else if (dbIndex instanceof DecommissionedDbIndex) {
            indexKey = field.getDataObjectType().getSimpleName();
        } else if (dbIndex instanceof PermissionsDbIndex) {
            indexKey = column.getTwo();
        } else if (dbIndex instanceof PrefixDbIndex) {
            indexKey = field.getPrefixIndexRowKey(UTF8Serializer.instance.deserialize(column.getValue()));
        } else if (dbIndex instanceof ScopedLabelDbIndex) {
            indexKey = field.getPrefixIndexRowKey(ScopedLabel.fromString(UTF8Serializer.instance.deserialize(column.getValue())));
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

    public static Object[] getIndexColumns(ColumnField field, CompositeColumnName column, String rowKey) {
        Object[] indexColumns = null;
        DbIndex dbIndex = field.getIndex();

        if (dbIndex instanceof AggregateDbIndex) {
            // Not support this index type yet.
            return indexColumns;
        }

        if (dbIndex instanceof NamedRelationDbIndex) {
            indexColumns = new String[4];
            indexColumns[0] = field.getDataObjectType().getSimpleName();
            NamedURI namedURI = NamedURI.fromString(UTF8Serializer.instance.deserialize(column.getValue()));
            String name = namedURI.getName();
            indexColumns[1] = name.toLowerCase();
            indexColumns[2] = name;
            indexColumns[3] = rowKey;

        } else if (dbIndex instanceof PrefixDbIndex) {
            indexColumns = new String[4];
            indexColumns[0] = field.getDataObjectType().getSimpleName();
            indexColumns[1] = UTF8Serializer.instance.deserialize(column.getValue()).toLowerCase();
            indexColumns[2] = UTF8Serializer.instance.deserialize(column.getValue());
            indexColumns[3] = rowKey;

        } else if (dbIndex instanceof ScopedLabelDbIndex) {
            indexColumns = new String[4];
            indexColumns[0] = field.getDataObjectType().getSimpleName();
            ScopedLabel label = ScopedLabel.fromString(UTF8Serializer.instance.deserialize(column.getValue()));
            indexColumns[1] = label.getLabel().toLowerCase();
            indexColumns[2] = label.getLabel();
            indexColumns[3] = rowKey;

        } else if (dbIndex instanceof DecommissionedDbIndex) {
            indexColumns = new String[2];
            Boolean val = BooleanSerializer.instance.deserialize(column.getValue());
            indexColumns[0] = val.toString();
            indexColumns[1] = rowKey;
        } else if (dbIndex instanceof ClassNameTimeSeriesDBIndex || dbIndex instanceof TimeSeriesDbIndex) {
            indexColumns = new Object[3];
            indexColumns[0] = field.getDataObjectType().getSimpleName();
            indexColumns[1] = UUIDs.unixTimestamp(column.getTimeUUID()) * 1000;
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
        DataObject dataObject = dbClient.queryObject(URI.create(key));
        return dataObject == null || dataObject.getInactive();
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
        try {
            ResultSet resultSet = dbClient.getLocalContext().getSession().execute(CQL_QUERY_SCHEMA_VERSION_TIMESTAMP);
            for (Row row : resultSet) {
                result.put(row.getLong(1), row.getString(0));
            }
        } catch (DriverException e) {
            _log.error("Failed to query schema versions", e);
        }
        
        return result;
    }
    
    public String findDataCreatedInWhichDBVersion(UUID timeUUID) {
        
        long createTime = 0;
        try {
            createTime = UUIDs.unixTimestamp(timeUUID) * 1000;
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
    
    public <T extends CompositeIndexColumnName> boolean isIndexExists(DbClientContext context, String indexCf, String indexKey, T column) throws DriverException {
        /*try {
        	StringBuilder queryString = new StringBuilder("select * from \"");
        	List<Object> parameters = new ArrayList<Object>();
        	
        	queryString.append(indexCf);
        	queryString.append("\" where key=?");
        	parameters.add(indexKey);
        	
            for (int i = 0; i < indexColumns.length; i++) {
                queryString.append(" and column").append(i+1).append("=?");
                parameters.add(indexColumns[i]);
            }
            
            ResultSet resultSet = context.getSession().execute(queryString.toString());
            return resultSet.one() != null;
        } catch (DriverException e) {
            return false;
        }*/
    	//TODO java driver
    	return true;
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
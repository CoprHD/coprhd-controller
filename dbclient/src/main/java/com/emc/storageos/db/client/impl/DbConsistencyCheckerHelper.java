/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.cassandra.serializers.BooleanSerializer;
import org.apache.cassandra.serializers.UTF8Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.exceptions.DriverException;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.exceptions.DatabaseException;

public class DbConsistencyCheckerHelper {
    private static final Logger _log = LoggerFactory.getLogger(DbConsistencyCheckerHelper.class);
    private static final int INDEX_OBJECTS_BATCH_SIZE = 10000;

    private DbClientImpl dbClient;

    public DbConsistencyCheckerHelper() {
    }

    public DbConsistencyCheckerHelper(DbClientImpl dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * Find out all rows in DataObject CFs that can't be deserialized,
     * such as such as object id cannot be converted to URI.
     *
     * @return number of corrupted rows
     */
    protected int checkDataObject(DataObjectType doType, boolean toConsole) {
        int dirtyCount = 0;
        _log.info("Check CF {}", doType.getDataObjectClass().getName());

        try {
            String queryString = String.format("select key from \"%s\"", doType.getCF().getName());
            SimpleStatement queryStatement = new SimpleStatement(queryString);
            queryStatement.setFetchSize(dbClient.DEFAULT_PAGE_SIZE);
            
            ResultSet resultSet = dbClient.getSession(doType.getDataObjectClass()).execute(queryStatement);
            
            for (Row row : resultSet) {
                try {
                    URI uri = URI.create(row.getString(0));
                    try {
                        dbClient.queryObject(doType.getDataObjectClass(), uri);
                    } catch (Exception ex) {
                        dirtyCount++;
                        logMessage(String.format(
                                "Inconsistency found: Fail to query object for '%s' with err %s ",
                                uri, ex.getMessage()), true, toConsole);
                    }
                } catch (Exception ex) {
                    dirtyCount++;
                    logMessage(String.format("Inconsistency found: Row key '%s' failed to convert to URI in CF %s with exception %s",
                    		row.getString(0), doType.getDataObjectClass()
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
    protected int checkCFIndices(DataObjectType doType, boolean toConsole) {
        int dirtyCount = 0;
        Class objClass = doType.getDataObjectClass();
        _log.info("Check Data Object CF {}", objClass);

        List<ColumnField> indexedFields = new ArrayList<>();
        for (ColumnField field : doType.getColumnFields()) {
            if (field.getIndex() == null) {
                continue;
            }
            indexedFields.add(field);
        }

        if (indexedFields.isEmpty()) {
            return dirtyCount;
        }

        String queryString = String.format("select key, inactive from \"%s\" where column1='%s'", doType.getCF().getName(),
                DataObject.INACTIVE_FIELD_NAME);
        SimpleStatement queryStatement = new SimpleStatement(queryString);
        queryStatement.setFetchSize(dbClient.DEFAULT_PAGE_SIZE);
        ResultSet resultSet = dbClient.getSession(doType.getDataObjectClass()).execute(queryStatement);
        
        for (Row row : resultSet) {
            List<URI> ids = new ArrayList<>(1);

            String key = row.getString(0);
            boolean inactive = row.getBool(1);
            
            if (inactive == true) {
            	continue;
            }

            ids.add(URI.create(key));
            
            for (ColumnField indexedField : indexedFields) {
				Map<String, List<CompositeColumnName>> result = dbClient
						.queryRowsWithAColumn(
								dbClient.getDbClientContext(doType
										.getDataObjectClass()), ids, doType
										.getCF().getName(), indexedField);
            	for (String rowKey : result.keySet()) {
                    List<CompositeColumnName> rows = result.get(rowKey);
                    for (CompositeColumnName column : rows) {
                        // we don't build index if the value is null, refer to ColumnField.
                        if (column == null) {
                            continue;
                        }
                        
                        String indexKey = getIndexKey(indexedField, column);
                        if (indexKey == null) {
                            continue;
                        }
                        
                        boolean isColumnInIndex = isColumnInIndex(doType, indexedField.getIndexCF().getName(), indexKey,
                                getIndexColumns(indexedField, column, rowKey));
                        if (!isColumnInIndex) {
                            dirtyCount++;
                            logMessage(String.format(
                                    "Inconsistency found Object(%s, id: %s, field: %s) is existing, but the related Index(%s, type: %s, id: %s) is missing.",
                                    indexedField.getDataObjectType().getSimpleName(), rowKey, indexedField.getName(),
                                    indexedField.getIndexCF().getName(), indexedField.getIndex().getClass().getSimpleName(), indexKey),
                                    true, toConsole);
                            DbCheckerFileWriter.writeTo(DbCheckerFileWriter.WRITER_REBUILD_INDEX,
                                    String.format("id:%s, cfName:%s", rowKey,
                                            indexedField.getDataObjectType().getSimpleName()));
                        }
                    }
                }
            }
        }

        return dirtyCount;
    }

    /**
     * Scan all the indices and related data object records, to find out
     * the index record is existing but the related data object records is missing.
     *
     * @return number of the corrupted rows in this index CF
     */
    protected int checkIndexingCF(IndexAndCf indexAndCf, boolean toConsole) {
        int corruptRowCount = 0;

        String indexCFName = indexAndCf.cf;
        Map<String, String> objCfs = getDataObjectCFs();
        _log.info("Start checking the index CF {}", indexCFName);

        Map<String, Map<String, List<IndexEntry>>> objsToCheck = new HashMap<>();
        
        SimpleStatement queryStatement = new SimpleStatement(String.format("select * from \"%s\"", indexAndCf.cf));
        queryStatement.setFetchSize(dbClient.DEFAULT_PAGE_SIZE);
        
        ResultSet resultSet = indexAndCf.dbClientContext.getSession().execute(queryStatement);

        for (Row row : resultSet) {
        	String key = row.getString(0);
            
            IndexColumnName indexColumnName = new IndexColumnName(row.getString(1), 
                    row.getString(2), 
                    row.getString(3),
                    row.getString(4),
                    row.getUUID(5),
                    row.getBytes(6)); 
            
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
                corruptRowCount += processBatchIndexObjects(indexAndCf, toConsole, objsToCheck);
            }
            
        }

        // Detect whether the DataObject CFs have the records
        corruptRowCount += processBatchIndexObjects(indexAndCf, toConsole, objsToCheck);

        return corruptRowCount;
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

    /*
     * We need to process index objects in batch to avoid occupy too many memory
     * */
    private int processBatchIndexObjects(IndexAndCf indexAndCf, boolean toConsole,
            Map<String, Map<String, List<IndexEntry>>> objsToCheck) {
        int corruptRowCount = 0;
        for (String objCf : objsToCheck.keySet()) {
            Map<String, List<IndexEntry>> objKeysIdxEntryMap = objsToCheck.get(objCf);
            
            String queryString = String.format("select distinct key from \"%s\" where key in ?", objCf);
            PreparedStatement queryStatement = indexAndCf.dbClientContext.getPreparedStatement(queryString);
            
            ResultSet resultSetByKey = indexAndCf.dbClientContext.getSession().execute(queryStatement.bind(objKeysIdxEntryMap.keySet()));
            
            Set<String> queryoutKeySet = new HashSet<String>();
            for (Row row : resultSetByKey) {
                queryoutKeySet.add(row.getString(0));
            }
            
            for (String key : objKeysIdxEntryMap.keySet()) {
                if (!queryoutKeySet.contains(key)) { // Only support all the columns have been removed now
                    List<IndexEntry> idxEntries = objKeysIdxEntryMap.get(key);
                    for (IndexEntry idxEntry : idxEntries) {
                        corruptRowCount++;
                        logMessage(String.format("Inconsistency found: Index(%s, type: %s, id: %s, column: %s) is existing "
                                + "but the related object record(%s, id: %s) is missing.",
                                indexAndCf.cf, indexAndCf.indexType.getSimpleName(),
                                idxEntry.getIndexKey(), idxEntry.getColumnName(),
                                objCf, key), true, toConsole);
                        DbCheckerFileWriter.writeTo(indexAndCf.dbClientContext.getKeyspaceName(),
                                String.format(
                                        "delete from \"%s\" where key='%s' and column1='%s' and column2='%s' and column3='%s' and column4='%s' and column5=%s;",
                                        indexAndCf.cf, idxEntry.getIndexKey(), idxEntry.getColumnName().getOne(),
                                        idxEntry.getColumnName().getTwo(),
                                        handleNullValue(idxEntry.getColumnName().getThree()),
                                        handleNullValue(idxEntry.getColumnName().getFour()),
                                        idxEntry.getColumnName().getTimeUUID()));

                    }

                }
            }
        }
        objsToCheck.clear();
        return corruptRowCount;
    }

    public Map<String, IndexAndCf> getAllIndices() {
        // Map<Index_CF_Name, <DbIndex, ColumnFamily, Map<Class_Name, object-CF_Name>>>
        Map<String, IndexAndCf> idxCfs = new TreeMap<>();
        for (DataObjectType objType : TypeMap.getAllDoTypes()) {
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
    protected static class IndexAndCf implements Comparable {
        private String cf;
        private Class<? extends DbIndex> indexType;
        private DbClientContext dbClientContext;

        IndexAndCf(Class<? extends DbIndex> indexType,
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

    class IndexEntry {
        private String indexKey;
        private IndexColumnName columnName;

        public IndexEntry(String indexKey, IndexColumnName columnName) {
            this.indexKey = indexKey;
            this.columnName = columnName;
        }

        public String getIndexKey() {
            return indexKey;
        }

        public IndexColumnName getColumnName() {
            return columnName;
        }

    }
    
    private ObjectEntry extractObjectEntryFromIndex(String indexKey,
            IndexColumnName name, Class<? extends DbIndex> type, boolean toConsole) {
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

    private boolean isColumnInIndex(DataObjectType doType, String indexCfName, String indexKey, String[] indexColumns) {
    	DbClientContext dbClientContext = dbClient.getDbClientContext(doType.getDataObjectClass());
    	StringBuilder queryString = new StringBuilder("select * from ");
    	List<Object> parameters = new ArrayList<Object>();
    	
    	queryString.append(indexCfName);
    	queryString.append(" where key=?");
    	parameters.add(indexKey);
    	
        for (int i = 0; i < indexColumns.length; i++) {
            queryString.append(" and column").append(i+1).append("=?");
            parameters.add(indexColumns[i]);
        }

        PreparedStatement preparedStatement = dbClientContext.getPreparedStatement(queryString.toString());
        _log.info("queryString: {}", preparedStatement.getQueryString());
        
        ResultSet resultSet = dbClient.getSession(doType.getDataObjectClass()).execute(preparedStatement.bind(parameters.toArray()));
        return resultSet.one() != null;
    }

    public static String getIndexKey(ColumnField field, CompositeColumnName column) {
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
        } else if (dbIndex instanceof AggregateDbIndex) {
            // Not support this index type yet.
        } else {
            String msg = String.format("Unsupported index type %s.", dbIndex.getClass());
            _log.warn(msg);
        }

        return indexKey;
    }

    public static String[] getIndexColumns(ColumnField field, CompositeColumnName column, String rowKey) {
        String[] indexColumns = null;
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
}

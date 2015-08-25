/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbutils;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbIndex;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.google.common.collect.Lists;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.query.ColumnFamilyQuery;
import com.netflix.astyanax.query.RowQuery;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.util.RangeBuilder;

import static com.emc.storageos.dbutils.DetectHelper.IndexAndCf;
import static com.emc.storageos.dbutils.DetectHelper.ObjectEntry;
import static com.emc.storageos.dbutils.DetectHelper.IndexEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InternalDbClientImpl extends InternalDbClient {
    private static final Logger log = LoggerFactory.getLogger(InternalDbClientImpl.class);

    private List<String> genTimeSeriesKeys(Calendar startTime, Calendar endTime) {
        final int KEY_SHARD = 10;// 10 shard for TimeSeries column family

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHH");
        List<String> keys = new ArrayList<String>();
        Calendar currentTime = startTime;
        while (true) {
            String timeTemp = dateFormat.format(currentTime.getTime());
            for (int i = 0; i < KEY_SHARD; i++) {
                keys.add(timeTemp + "-" + i);
            }
            currentTime.add(Calendar.HOUR, 1);
            if (currentTime.compareTo(endTime) > 0) {
                break;
            }
        }
        return keys;
    }

    public int countTimeSeries(String cfName, Calendar startTime, Calendar endTime) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd/HH");
        String startTimeStr = dateFormat.format(startTime.getTime());
        String endTimeStr = dateFormat.format(endTime.getTime());
        int recordCount = 0;
        try {
            Keyspace keyspace = getLocalKeyspace();

            ColumnFamily<String, String> cf = new ColumnFamily<String, String>(
                    cfName, StringSerializer.get(), StringSerializer.get());

            List<String> keys = genTimeSeriesKeys(startTime, endTime);
            for (String key : keys) {
                recordCount += keyspace.prepareQuery(cf).getKey(key).getCount().execute().getResult();
            }
            System.out.println(String.format("Column Family %s's record count between %s and %s is: %s",
                    cfName, startTimeStr, endTimeStr, recordCount));

            return recordCount;
        } catch (ConnectionException e) {
            System.err.println(String.format("Exception=%s", e));
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    /**
     * Find out all rows in DataObject CFs that can't be deserialized,
     * such as such as object id cannot be converted to URI.
     * 
     * @return True, when no corrupted data found
     */
    public boolean checkDataObjects() {
        logAndPrintToScreen("Start to check dirty data that cannot be deserialized.");

        int cfCount = 0;
        int dirtyCount = 0;

        for (DataObjectType doType : TypeMap.getAllDoTypes()) {
            cfCount++;
            log.info("Check CF {}", doType.getDataObjectClass().getName());
            try {
                OperationResult<Rows<String, CompositeColumnName>> result = getKeyspace(
                        doType.getDataObjectClass()).prepareQuery(doType.getCF())
                        .getAllRows().setRowLimit(100)
                        .withColumnRange(new RangeBuilder().setLimit(1).build())
                        .execute();
                for (Row<String, CompositeColumnName> row : result.getResult()) {
                    if (!row.getColumns().isEmpty()) {
                        try {
                            URI uri = URI.create(row.getKey());
                            try {
                                queryObject(doType.getDataObjectClass(), uri);
                            } catch (Exception ex) {
                                dirtyCount++;
                                logAndPrintToScreen(String.format(
                                        "Fail to query object for '%s' with err %s ",
                                        uri, ex.getMessage()), true);
                            }
                        } catch (Exception ex) {
                            dirtyCount++;
                            logAndPrintToScreen(
                                    String.format("Row key '%s' failed to convert to URI in CF %s with exception %s",
                                            row.getKey(), doType.getDataObjectClass()
                                                    .getName(), ex.getMessage()), true);
                        }
                    }
                }

            } catch (ConnectionException e) {
                throw DatabaseException.retryables.connectionFailed(e);
            }
        }

        logAndPrintToScreen(String.format("%nTotally check %d cfs, %d rows are dirty.%n",
                cfCount, dirtyCount));
        return dirtyCount == 0;
    }

    /**
     * Scan all the indices and related data object records, to find out
     * the index record is existing but the related data object records is missing.
     * 
     * @return True, when no corrupted data found
     * @throws ConnectionException
     */
    public boolean checkIndexingCFs() throws ConnectionException {
        logAndPrintToScreen("\nStart to check INDEX data that the related object records are missing.\n");

        Collection<IndexAndCf> idxCfs = getAllIndices().values();
        Map<String, ColumnFamily<String, CompositeColumnName>> objCfs = getDataObjectCFs();
        int indexRowCount = 0;
        int objCfCount = 0;
        int objRowCount = 0;
        int corruptRowCount = 0;

        for (IndexAndCf indexAndCf : idxCfs) {
            int corruptRowCountInIdx = 0;
            log.info("Check Index CF {}", indexAndCf.cf.getName());
            Map<ColumnFamily<String, CompositeColumnName>, Map<String, List<IndexEntry>>> objsToCheck = new HashMap<>();

            ColumnFamilyQuery<String, IndexColumnName> query = indexAndCf.keyspace
                    .prepareQuery(indexAndCf.cf);

            OperationResult<Rows<String, IndexColumnName>> result = query.getAllRows()
                    .setRowLimit(100)
                    .withColumnRange(new RangeBuilder().setLimit(0).build()).execute();

            for (Row<String, IndexColumnName> row : result.getResult()) {
                indexRowCount++;
                RowQuery<String, IndexColumnName> rowQuery = query.getRow(row.getKey())
                        .autoPaginate(true)
                        .withColumnRange(new RangeBuilder().setLimit(100).build());
                ColumnList<IndexColumnName> columns;
                while (!(columns = rowQuery.execute().getResult()).isEmpty()) {
                    for (Column<IndexColumnName> column : columns) {
                        ObjectEntry objEntry = DetectHelper
                                .extractObjectEntryFromIndex(row.getKey(),
                                        column.getName(), indexAndCf.indexType);
                        if (objEntry == null) {
                            continue;
                        }
                        ColumnFamily<String, CompositeColumnName> objCf = objCfs
                                .get(objEntry.getClassName());

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
                }
            }

            objCfCount += objsToCheck.size();
            // Detect whether the DataObject CFs have the records
            for (ColumnFamily<String, CompositeColumnName> objCf : objsToCheck.keySet()) {
                Map<String, List<IndexEntry>> objKeysIdxEntryMap = objsToCheck.get(objCf);
                OperationResult<Rows<String, CompositeColumnName>> objResult = indexAndCf.keyspace
                        .prepareQuery(objCf).getRowSlice(objKeysIdxEntryMap.keySet())
                        .withColumnRange(new RangeBuilder().setLimit(1).build())
                        .execute();
                for (Row<String, CompositeColumnName> row : objResult.getResult()) {
                    objRowCount++;
                    if (row.getColumns().isEmpty()) { // Only support all the columns have been removed now
                        List<IndexEntry> idxEntries = objKeysIdxEntryMap.get(row.getKey());
                        for (IndexEntry idxEntry : idxEntries) {
                            corruptRowCount++;
                            corruptRowCountInIdx++;
                            logAndPrintToScreen(String.format("Index(%s, type: %s, id: %s, column: %s) is existing "
                                    + "but the related object record(%s, id: %s) is missing.",
                                    indexAndCf.cf.getName(), indexAndCf.indexType.getSimpleName(),
                                    idxEntry.getIndexKey(), idxEntry.getColumnName(),
                                    objCf.getName(), row.getKey()), true);
                        }

                    }
                }
            }

            if (corruptRowCountInIdx != 0) {
                logAndPrintToScreen(String.format(
                        "\n%d corrupted index records found in Index %s of Index type %s.\n",
                        corruptRowCountInIdx, indexAndCf.cf.getName(),
                        indexAndCf.indexType.getSimpleName()), true);
            }
        }

        logAndPrintToScreen(String.format(
                "\nFinish to check INDEX data, totally check %s rows of %s indices and %s rows of %s object cfs, "
                        + "%s corrupted data found.", indexRowCount, idxCfs.size(), objRowCount, objCfCount, corruptRowCount));

        return corruptRowCount == 0;
    }

    public Map<String, IndexAndCf> getAllIndices() {
        Map<String, IndexAndCf> idxCfs = new TreeMap<>(); // Map<Index_CF_Name, <DbIndex, ColumnFamily, Map<Class_Name, object-CF_Name>>>
        for (DataObjectType objType : TypeMap.getAllDoTypes()) {
            Keyspace keyspace = getKeyspace(objType.getDataObjectClass());
            for (ColumnField field : objType.getColumnFields()) {
                DbIndex index = field.getIndex();
                if (index == null) {
                    continue;
                }

                String key = IndexAndCf.generateKey(index.getClass(), field.getIndexCF(),
                        keyspace);
                IndexAndCf idxAndCf = idxCfs.get(key);
                if (idxAndCf == null) {
                    idxAndCf = new IndexAndCf(index.getClass(), field.getIndexCF(),
                            keyspace);
                    idxCfs.put(key, idxAndCf);
                }
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

    private void logAndPrintToScreen(String msg, boolean isError) {
        if (isError) {
            log.error(msg);
            System.err.println(msg);
        } else {
            log.info(msg);
            System.out.println(msg);
        }
    }

    private void logAndPrintToScreen(String msg) {
        logAndPrintToScreen(msg, false);
    }
    
    public Column<CompositeColumnName> getLatestModifiedField(DataObjectType type, URI id) {
        Column<CompositeColumnName> latestField = null;
        ColumnFamily<String, CompositeColumnName> cf = type.getCF();
        Keyspace ks = this.getKeyspace(type.getDataObjectClass());
        Rows<String, CompositeColumnName> rows = this.queryRowsWithAllColumns(ks,
                Lists.newArrayList(id), cf);
        if (rows.isEmpty()) {
            log.warn("Can not find the latest modified field of {}", id);
            return latestField;
        }
        
        long latestTimeStampe = 0;
        for (Column<CompositeColumnName> column : rows.iterator().next().getColumns()) {
            if (column.getTimestamp() > latestTimeStampe) {
                latestTimeStampe = column.getTimestamp();
                latestField = column;
            }
        }

        return latestField;
    }
}

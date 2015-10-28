/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import com.emc.storageos.db.client.impl.DbClientImpl.IndexAndCf;
import com.netflix.astyanax.model.ColumnFamily;

public class DbChecker {
    private DbClientImpl dbClient;

    public DbChecker(DbClientImpl dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * Find out all rows in DataObject CFs that can't be deserialized,
     * such as such as object id cannot be converted to URI.
     *
     * @return number of the corrupted rows in data CFs
     */
    public int checkDataObjects(boolean toConsole) {
        dbClient.logMessage("Start to check dirty data that cannot be deserialized.", false, toConsole);

        int cfCount = 0;
        int dirtyCount = 0;

        for (DataObjectType doType : TypeMap.getAllDoTypes()) {
            cfCount++;
            dirtyCount += dbClient.checkDataObject(doType, toConsole);
        }

        String msg = String.format("\nTotally check %d cfs, %d rows are dirty.\n", cfCount, dirtyCount);
        dbClient.logMessage(msg, false, toConsole);

        return dirtyCount;
    }

    /**
     * Scan all the indices and related data object records, to find out
     * the index record is existing but the related data object records is missing.
     *
     * @return the number of the orrupted rows in the index CFs
     * @throws ConnectionException
     */
    public int checkIndexingCFs(boolean toConsole) throws ConnectionException {
        dbClient.logMessage("\nStart to check INDEX data that the related object records are missing.\n", false, toConsole);

        int corruptRowCount = 0;
        Collection<IndexAndCf> idxCfs = getAllIndices().values();
        Map<String, ColumnFamily<String, CompositeColumnName>> objCfs = getDataObjectCFs();

        for (DbClientImpl.IndexAndCf indexAndCf : idxCfs) {
            corruptRowCount += dbClient.checkIndexingCF(indexAndCf, objCfs, toConsole);
        }

        String msg = String.format("\nFinish to check INDEX CFs: totally checked %d indices against %d data CFs "+
                   "and %d corrupted rows found.", idxCfs.size(), objCfs.size(), corruptRowCount);

        dbClient.logMessage(msg, false, toConsole);

        return corruptRowCount;
    }

    private Map<String, DbClientImpl.IndexAndCf> getAllIndices() {
        // Map<Index_CF_Name, <DbIndex, ColumnFamily, Map<Class_Name, object-CF_Name>>>
        Map<String, IndexAndCf> idxCfs = new TreeMap<>();
        for (DataObjectType objType : TypeMap.getAllDoTypes()) {
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
        }

        return idxCfs;
    }

    private Map<String, ColumnFamily<String, CompositeColumnName>> getDataObjectCFs() {
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
}

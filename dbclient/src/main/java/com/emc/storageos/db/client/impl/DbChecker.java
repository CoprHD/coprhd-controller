/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbConsistencyStatus;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.impl.DbClientImpl.IndexAndCf;
import com.netflix.astyanax.model.ColumnFamily;

public class DbChecker {
    private DbClientImpl dbClient;
    private CoordinatorClient coordinator;
    private int cfCount;

    public DbChecker(DbClientImpl dbClient) {
        this.dbClient = dbClient;
        this.cfCount = TypeMap.getAllDoTypes().size();
    }

    /**
     * Find out all rows in DataObject CFs that can't be deserialized,
     * such as such as object id cannot be converted to URI.
     *
     * @return number of the corrupted rows in data CFs
     */
    public int checkDataObjects(boolean toConsole) {
        dbClient.logMessage("Start to check dirty data that cannot be deserialized.", false, toConsole);

        Collection<DataObjectType> allDoTypes = TypeMap.getAllDoTypes();
        Collection<DataObjectType> sortedTypes = getAllDoTypes(allDoTypes);
        int cfCount = allDoTypes.size();
        DbConsistencyStatus status = getStatusFromZk();
        Collection<DataObjectType> filteredTypes = filterOutTypes(sortedTypes, status.getWorkingPoint(), toConsole);
        int dirtyCount = 0;
        
        for (DataObjectType doType : filteredTypes) {
            if ( !toConsole && isCancelled() ) {
                cancel(status);
                return dirtyCount;
            }
            if (!toConsole) {
                status.updateCFProgress(cfCount, doType.getCF().getName(), dirtyCount);
                persistStatus(status);
            }
            
            dirtyCount += dbClient.checkDataObject(doType, toConsole);
        }

        String msg = String.format("\nTotally check %d cfs, %d rows are dirty.\n", cfCount, dirtyCount);
        dbClient.logMessage(msg, false, toConsole);

        return dirtyCount;
    }

    private void cancel(DbConsistencyStatus status) {
        dbClient.logMessage("db consistench check is canceled", true, false);
        status.movePreviousBack();
        persistStatus(status);
    }

    private boolean isCancelled() {
        DbConsistencyStatus status = getStatusFromZk();
        return status.isCancelled();
    }

    public void persistStatus(DbConsistencyStatus status) {
        this.coordinator.persistRuntimeState(Constants.DB_CONSISTENCY_STATUS, status);
    }

    private Collection<DataObjectType> filterOutTypes(Collection<DataObjectType> types, String workingPoint, boolean toConsole) {
        if (toConsole) {
            return types;
        }
        
        if (workingPoint == null) {
            return types;
        }
        
        boolean found = false;
        List<DataObjectType> filteredTypes = new ArrayList<DataObjectType> ();
        for(DataObjectType type : types) {
            if (workingPoint.equals(type.getCF().getName())) {
                found = true;
            }
            if (found) {
                filteredTypes.add(type);
            }
        }
        return filteredTypes;
    }
    
    private Collection<IndexAndCf> filterOutIndexAndCfs(Collection<IndexAndCf> idxCfs, String workingPoint, boolean toConsole) {
        if (toConsole) {
            return idxCfs;
        }
        
        if (workingPoint == null) {
            return idxCfs;
        }
        
        boolean found = false;
        List<IndexAndCf> filteredIdxAndCfs = new ArrayList<IndexAndCf> ();
        for(IndexAndCf idxCf : idxCfs) {
            if (workingPoint.equals(idxCf.generateKey())) {
                found = true;
            }
            if (found) {
                filteredIdxAndCfs.add(idxCf);
            }
        }
        return found? filteredIdxAndCfs : idxCfs;
    }
    
    public DbConsistencyStatus getStatusFromZk() {
        return this.coordinator.queryRuntimeState(Constants.DB_CONSISTENCY_STATUS, DbConsistencyStatus.class);
    }

    private Collection<DataObjectType> getAllDoTypes(Collection<DataObjectType> allDoTypes) {
        List<DataObjectType> types = new ArrayList<DataObjectType>(allDoTypes);
        Collections.sort(types, new Comparator<DataObjectType>() {

            @Override
            public int compare(DataObjectType type, DataObjectType anotherType) {
                return type.getCF().getName().compareTo(anotherType.getCF().getName());
            }
        });
        return Collections.unmodifiableCollection(types);
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

        Collection<IndexAndCf> idxCfs = getAllIndices().values();
        Map<String, ColumnFamily<String, CompositeColumnName>> objCfs = getDataObjectCFs();
        DbConsistencyStatus status = getStatusFromZk();
        Collection<IndexAndCf> sortedIdxCfs = sortIndexCfs(idxCfs);
        Collection<IndexAndCf> filteredIdCfs = filterOutIndexAndCfs(sortedIdxCfs, status.getWorkingPoint(), toConsole);
        int corruptRowCount = 0;
        int totalCorruptCount = 0;
        
        for (DbClientImpl.IndexAndCf indexAndCf : filteredIdCfs) {
            if ( !toConsole && isCancelled() ) {
                cancel(status);
                return totalCorruptCount;
            }
            
            if (!toConsole) {
                status.updateIndexProgress(this.cfCount+idxCfs.size(), indexAndCf.generateKey(), corruptRowCount);
                persistStatus(status);
            }
            corruptRowCount = dbClient.checkIndexingCF(indexAndCf, objCfs, toConsole);
            totalCorruptCount += corruptRowCount;
        }

        String msg = String.format("\nFinish to check INDEX CFs: totally checked %d indices against %d data CFs "+
                   "and %d corrupted rows found.", idxCfs.size(), objCfs.size(), totalCorruptCount);

        dbClient.logMessage(msg, false, toConsole);

        return totalCorruptCount;
    }

    @SuppressWarnings("unchecked")
    private Collection<IndexAndCf> sortIndexCfs(Collection<IndexAndCf> idxCfs) {
        List<IndexAndCf> list = new ArrayList<IndexAndCf>(idxCfs);
        Collections.sort(list);
        return Collections.unmodifiableCollection(list);
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

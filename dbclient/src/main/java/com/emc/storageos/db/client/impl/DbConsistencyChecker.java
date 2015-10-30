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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbConsistencyStatus;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.impl.DbClientImpl.IndexAndCf;
import com.netflix.astyanax.model.ColumnFamily;

public class DbConsistencyChecker {
    private static final Logger log = LoggerFactory.getLogger(DbConsistencyChecker.class);
    private DbClientImpl dbClient;
    private CoordinatorClient coordinator;
    private int totalCount;
    private static final String OBJECT_URI = "objectUri_";
    private static final String OBJECT_INDICES = "objectIndices_";
    private static final String INDEX_OBJECTS = "indexObjects_";
    
    public DbConsistencyChecker() {
    }

    public DbConsistencyChecker(DbClientImpl dbClient) {
        this.dbClient = dbClient;
    }
    
    public void init() {
        int cfCount = TypeMap.getAllDoTypes().size();
        int indexCount = getAllIndices().values().size();
        this.totalCount = indexCount + cfCount*2;
    }

    /**
     * Find out all rows in DataObject CFs that can't be deserialized,
     * such as such as object id cannot be converted to URI.
     *
     * @return number of the corrupted rows in data CFs
     */
    public int checkObjectId(boolean toConsole) {
        Collection<DataObjectType> allDoTypes = TypeMap.getAllDoTypes();
        Collection<DataObjectType> sortedTypes = getSortedTypes(allDoTypes);
        DbConsistencyStatus status = getStatusFromZk();
        dbClient.logMessage(String.format("status %s in zk", status.toString()), false, false);
        Collection<DataObjectType> filteredTypes = filterOutTypes(sortedTypes, status.getWorkingPoint(), toConsole);
        
        int totalDirtyCount = 0;
        int dirtyCount = 0;
        for (DataObjectType doType : filteredTypes) {
            dbClient.logMessage(String.format("processing %s cf", doType.getCF().getName()), false, false);
            if ( !toConsole && isCancelled() ) {
                cancel(status);
                return totalDirtyCount;
            }
            if (!toConsole) {
                status.update(this.totalCount, OBJECT_URI+doType.getCF().getName(), dirtyCount);
                persistStatus(status);
            }
            
            dirtyCount = dbClient.checkDataObject(doType, toConsole);
            totalDirtyCount += dirtyCount;
        }

        String msg = String.format("\nTotally check %d cfs, %d rows are dirty.\n", filteredTypes.size(), totalDirtyCount);
        dbClient.logMessage(msg, false, toConsole);

        return totalDirtyCount;
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

    private Collection<DataObjectType> getSortedTypes(Collection<DataObjectType> allDoTypes) {
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
     * @param toConsole whether print out in the console
     * @return the number of the corrupted rows in the index CFs
     * @throws ConnectionException
     */
    public int checkIndexObjects(boolean toConsole) throws ConnectionException {
        dbClient.logMessage("\nStart to check INDEX data that the related object records are missing.\n", false, toConsole);

        Collection<IndexAndCf> idxCfs = getAllIndices().values();
        Map<String, ColumnFamily<String, CompositeColumnName>> objCfs = getDataObjectCFs();
        DbConsistencyStatus status = getStatusFromZk();
        Collection<IndexAndCf> sortedIdxCfs = sortIndexCfs(idxCfs);
        Collection<IndexAndCf> filteredIdCfs = filterOutIndexAndCfs(sortedIdxCfs, status.getWorkingPoint(), toConsole);
        int corruptRowCount = 0;
        int totalCorruptCount = 0;
        
        for (DbClientImpl.IndexAndCf indexAndCf : filteredIdCfs) {
            dbClient.logMessage(String.format("indexAndCf ", indexAndCf.generateKey()), false, toConsole);
            if ( !toConsole && isCancelled() ) {
                cancel(status);
                return totalCorruptCount;
            }
            
            if (!toConsole) {
                status.update(this.totalCount, INDEX_OBJECTS+indexAndCf.generateKey(), corruptRowCount);
                persistStatus(status);
            }
            corruptRowCount = dbClient.checkIndexingCF(indexAndCf, objCfs, toConsole);
            totalCorruptCount += corruptRowCount;
        }

        DbCheckerFileWriter.close();

        String msg = String.format("\nFinish to check INDEX CFs: totally checked %d indices against %d data CFs "+
                   "and %d corrupted rows found.", idxCfs.size(), objCfs.size(), totalCorruptCount);

        dbClient.logMessage(msg, false, toConsole);

        return totalCorruptCount;
    }

    /**
     * Scan all the data object records, to find out the object record is existing
     * but the related index is missing.
     *
     * @param toConsole whether print out in the console
     * @return True, when no corrupted data found
     * @throws ConnectionException
     */
    public int checkObjectIndices(boolean toConsole) throws ConnectionException {
        dbClient.logMessage("\nStart to check Data Object records that the related index is missing.\n", false, toConsole);

        Collection<DataObjectType> allDoTypes = TypeMap.getAllDoTypes();
        Collection<DataObjectType> sortedTypes = getSortedTypes(allDoTypes);
        int cfCount = allDoTypes.size();
        DbConsistencyStatus status = getStatusFromZk();
        Collection<DataObjectType> filteredTypes = filterOutTypes(sortedTypes, status.getWorkingPoint(), toConsole);
        int totalCorruptedCount = 0;
        int corruptedCount = 0;

        for (DataObjectType doType : filteredTypes) {
            if (!toConsole && isCancelled()) {
                cancel(status);
                return totalCorruptedCount;
            }
            if (!toConsole) {
                status.update(this.totalCount, OBJECT_INDICES+doType.getCF().getName(), corruptedCount);
                persistStatus(status);
            }

            corruptedCount = dbClient.checkCFIndices(doType, toConsole);
            totalCorruptedCount += corruptedCount;
        }

        DbCheckerFileWriter.close();

        String msg = String.format(
                "\nFinish to check DataObject CFs: totally checked %d data CFs, "
                        + "%d corrupted rows found.",
                cfCount, totalCorruptedCount);

        dbClient.logMessage(msg, false, toConsole);

        return totalCorruptedCount;
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
    
    public DbClientImpl getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClientImpl dbClient) {
        this.dbClient = dbClient;
    }

    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }
}
/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbConsistencyStatus;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper.IndexAndCf;

public class DbConsistencyChecker {
    private static final Logger log = LoggerFactory.getLogger(DbConsistencyChecker.class);
    private CoordinatorClient coordinator;
    private DbConsistencyCheckerHelper helper;
    private int totalCount;
    private boolean toConsole;

    private static final String OBJECT_URI = "objectUri_";
    private static final String OBJECT_INDICES = "objectIndices_";
    private static final String INDEX_OBJECTS = "indexObjects_";
    
    public DbConsistencyChecker() {
    }

    public DbConsistencyChecker(DbConsistencyCheckerHelper helper, boolean toConsole) {
        this.helper = helper;
        this.toConsole = toConsole;
    }
    
    public void init() {
        int cfCount = TypeMap.getAllDoTypes().size();
        int indexCount = helper.getAllIndices().values().size();
        this.totalCount = indexCount + cfCount*2;
        log.info(String.format("cfCount=%d indexCount=%d totalCount=%d", cfCount, indexCount, this.totalCount));
    }

    /**
     * Find out all rows in DataObject CFs that can't be deserialized,
     * such as such as object id cannot be converted to URI.
     *
     * @return number of the corrupted rows in data CFs
     */
    public int checkObjectId() {
        Collection<DataObjectType> allDoTypes = TypeMap.getAllDoTypes();
        Collection<DataObjectType> sortedTypes = getSortedTypes(allDoTypes);
        DbConsistencyStatus status = getStatusFromZk();
        helper.logMessage(String.format("status %s in zk", status.toString()), false, false);
        Collection<DataObjectType> filteredTypes = filterOutTypes(sortedTypes, OBJECT_URI, status.getWorkingPoint(), toConsole);
        
        int totalDirtyCount = 0;
        int dirtyCount = 0;
        for (DataObjectType doType : filteredTypes) {
            helper.logMessage(String.format("processing %s cf", doType.getCF().getName()), false, false);
            if ( !toConsole && isCancelled() ) {
                cancel(status);
                return totalDirtyCount;
            }
            if (!toConsole) {
                status.update(this.totalCount, OBJECT_URI+doType.getCF().getName(), dirtyCount);
                persistStatus(status);
            }
            
            dirtyCount = helper.checkDataObject(doType, toConsole);
            totalDirtyCount += dirtyCount;
        }

        String msg = String.format("\nTotally check %d cfs, %d rows are dirty.\n", filteredTypes.size(), totalDirtyCount);
        helper.logMessage(msg, false, toConsole);

        return totalDirtyCount;
    }

    private void cancel(DbConsistencyStatus status) {
        helper.logMessage("db consistench check is canceled", true, false);
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

    private Collection<DataObjectType> filterOutTypes(Collection<DataObjectType> types, String prefex, String workingPoint, boolean toConsole) {
        if (toConsole) {
            return types;
        }
        
        if (workingPoint == null) {
            return types;
        }
        
        boolean found = false;
        List<DataObjectType> filteredTypes = new ArrayList<DataObjectType> ();
        for(DataObjectType type : types) {
            if (workingPoint.equals(prefex+type.getCF().getName())) {
                found = true;
            }
            if (found) {
                filteredTypes.add(type);
            }
        }
        return filteredTypes;
    }
    
    private Collection<IndexAndCf> filterOutIndexAndCfs(Collection<DbConsistencyCheckerHelper.IndexAndCf> idxCfs, String prefex, String workingPoint, boolean toConsole) {
        if (toConsole) {
            return idxCfs;
        }
        
        if (workingPoint == null) {
            return idxCfs;
        }
        
        boolean found = false;
        List<IndexAndCf> filteredIdxAndCfs = new ArrayList<IndexAndCf> ();
        for(IndexAndCf idxCf : idxCfs) {
            if (workingPoint.equals(prefex+idxCf.generateKey())) {
                found = true;
            }
            if (found) {
                filteredIdxAndCfs.add(idxCf);
            }
        }
        return found? filteredIdxAndCfs : idxCfs;
    }
    
    public DbConsistencyStatus getStatusFromZk() {
        if (toConsole) {
            return new DbConsistencyStatus();
        }
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
    public int checkIndexObjects() throws ConnectionException {
        helper.logMessage("\nStart to check INDEX data that the related object records are missing.\n", false, toConsole);

        Collection<IndexAndCf> idxCfs = helper.getAllIndices().values();
        Map<String, ColumnFamily<String, CompositeColumnName>> objCfs = helper.getDataObjectCFs();
        DbConsistencyStatus status = getStatusFromZk();
        Collection<IndexAndCf> sortedIdxCfs = sortIndexCfs(idxCfs);
        Collection<IndexAndCf> filteredIdCfs = filterOutIndexAndCfs(sortedIdxCfs, INDEX_OBJECTS, status.getWorkingPoint(), toConsole);
        int corruptRowCount = 0;
        int totalCorruptCount = 0;
        
        for (IndexAndCf indexAndCf : filteredIdCfs) {
            helper.logMessage(String.format("indexAndCf ", indexAndCf.generateKey()), false, toConsole);
            if ( !toConsole && isCancelled() ) {
                cancel(status);
                return totalCorruptCount;
            }
            
            if (!toConsole) {
                status.update(this.totalCount, INDEX_OBJECTS+indexAndCf.generateKey(), corruptRowCount);
                persistStatus(status);
            }
            corruptRowCount = helper.checkIndexingCF(indexAndCf, objCfs, toConsole);
            totalCorruptCount += corruptRowCount;
        }

        DbCheckerFileWriter.close();

        String msg = String.format("\nFinish to check INDEX CFs: totally checked %d indices against %d data CFs "+
                   "and %d corrupted rows found.", idxCfs.size(), objCfs.size(), totalCorruptCount);

        helper.logMessage(msg, false, toConsole);

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
    public int checkObjectIndices() throws ConnectionException {
        helper.logMessage("\nStart to check Data Object records that the related index is missing.\n", false, toConsole);

        Collection<DataObjectType> allDoTypes = TypeMap.getAllDoTypes();
        Collection<DataObjectType> sortedTypes = getSortedTypes(allDoTypes);
        
        DbConsistencyStatus status = getStatusFromZk();
        Collection<DataObjectType> filteredTypes = filterOutTypes(sortedTypes, OBJECT_INDICES, status.getWorkingPoint(), toConsole);
        int totalCorruptedCount = 0;
        int corruptedCount = 0;
        int cfCount = allDoTypes.size();

        for (DataObjectType doType : filteredTypes) {
            if (!toConsole && isCancelled()) {
                cancel(status);
                return totalCorruptedCount;
            }
            if (!toConsole) {
                status.update(this.totalCount, OBJECT_INDICES+doType.getCF().getName(), corruptedCount);
                persistStatus(status);
            }

            corruptedCount = helper.checkCFIndices(doType, toConsole);
            totalCorruptedCount += corruptedCount;
        }

        DbCheckerFileWriter.close();

        String msg = String.format(
                "\nFinish to check DataObject CFs: totally checked %d data CFs, "
                        + "%d corrupted rows found.",
                cfCount, totalCorruptedCount);

        helper.logMessage(msg, false, toConsole);

        return totalCorruptedCount;
    }

    @SuppressWarnings("unchecked")
    private Collection<IndexAndCf> sortIndexCfs(Collection<IndexAndCf> idxCfs) {
        List<IndexAndCf> list = new ArrayList<IndexAndCf>(idxCfs);
        Collections.sort(list);
        return Collections.unmodifiableCollection(list);
    }

    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public boolean isToConsole() {
        return toConsole;
    }

    public void setToConsole(boolean toConsole) {
        this.toConsole = toConsole;
    }

    public DbConsistencyCheckerHelper getHelper() {
        return helper;
    }

    public void setHelper(DbConsistencyCheckerHelper helper) {
        this.helper = helper;
    }

}
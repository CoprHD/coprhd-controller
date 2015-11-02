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
import java.util.concurrent.CancellationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbConsistencyStatus;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper.IndexAndCf;

public class DbConsistencyChecker {
    private static final Logger log = LoggerFactory.getLogger(DbConsistencyChecker.class);
    private CoordinatorClient coordinator;
    private DbConsistencyCheckerHelper helper;
    private int totalCount;
    // Print out the result to console directly and don't save the status in ZK, used such as triggered by dbutils
    private boolean toConsole;

    public DbConsistencyChecker() {
    }

    public DbConsistencyChecker(DbConsistencyCheckerHelper helper, boolean toConsole) {
        this.helper = helper;
        this.toConsole = toConsole;
    }

    public int check() throws ConnectionException {
        init();
        int corruptedCount = 0;
        CheckType checkType = getCheckTypeFromZK();
        log.info("db consistency check type:{}", checkType);
        switch (checkType) {
            case OBJECT_ID:
                corruptedCount += checkObjectId();
                setNextCheckType();
            case OBJECT_INDICES:
                corruptedCount += checkObjectIndices();
                setNextCheckType();
            case INDEX_OBJECTS:
                corruptedCount += checkIndexObjects();
        }

        return corruptedCount;
    }

    public void persistStatus(DbConsistencyStatus status) {
        this.coordinator.persistRuntimeState(Constants.DB_CONSISTENCY_STATUS, status);
    }

    public DbConsistencyStatus getStatusFromZk() {
        if (toConsole) {
            return new DbConsistencyStatus();
        }
        return this.coordinator.queryRuntimeState(Constants.DB_CONSISTENCY_STATUS, DbConsistencyStatus.class);
    }

    private void init() {
        if (toConsole) {
            return;
        }

        int cfCount = TypeMap.getAllDoTypes().size();
        int indexCount = helper.getAllIndices().values().size();
        this.totalCount = indexCount + cfCount * 2;
        log.info(String.format("cfCount=%d indexCount=%d totalCount=%d", cfCount, indexCount, this.totalCount));
    }

    private void setNextCheckType() {
        if (toConsole) {
            return;
        }

        CheckType checkType = getCheckTypeFromZK();
        
        if(!checkType.hasNext()){
            return;
        }
        DbConsistencyStatus status = getStatusFromZk();
        status.update(checkType.getNext().name(), null);
        persistStatus(status);
    }

    /**
     * Find out all rows in DataObject CFs that can't be deserialized,
     * such as such as object id cannot be converted to URI.
     *
     * @return number of the corrupted rows in data CFs
     */
    private int checkObjectId() {
        CheckType checkType = CheckType.OBJECT_ID;
        helper.logMessage("\nStart to check DataObject records id that is illegal.\n", false, toConsole);

        DbConsistencyStatus status = getStatusFromZk();
        Collection<DataObjectType> resumeDataCfs = resumeFromWorkingPoint(checkType, status.getWorkingPoint());

        int totalIllegalCount = 0;
        for (DataObjectType dataCf : resumeDataCfs) {
            int illegalCount = helper.checkDataObject(dataCf, toConsole);
            status = getStatusFromZk();
            if (!toConsole && isCancelled(status)) {
                cancel(status);
            }
            if (!toConsole) {
                status.update(this.totalCount, checkType.name(), dataCf.getCF().getName(), illegalCount);
                persistStatus(status);
            }
            totalIllegalCount += illegalCount;
        }

        String msg = String.format("\nFinish to check DataObject records id: totally checked %d data CFs, "
                + "%d corrupted rows found.\n", resumeDataCfs.size(), totalIllegalCount);
        helper.logMessage(msg, false, toConsole);

        return totalIllegalCount;
    }

    /**
     * Scan all the data object records, to find out the object record is existing
     * but the related index is missing.
     *
     * @param toConsole whether print out in the console
     * @return True, when no corrupted data found
     * @throws ConnectionException
     */
    private int checkObjectIndices() throws ConnectionException {
        CheckType checkType = CheckType.OBJECT_INDICES;
        helper.logMessage("\nStart to check DataObject records that the related index is missing.\n", false, toConsole);

        DbConsistencyStatus status = getStatusFromZk();
        Collection<DataObjectType> resumeDataCfs = resumeFromWorkingPoint(checkType, status.getWorkingPoint());

        int totalCorruptedCount = 0;
        for (DataObjectType dataCf : resumeDataCfs) {
            int corruptedCount = helper.checkCFIndices(dataCf, toConsole);
            status = getStatusFromZk();
            if (!toConsole && isCancelled(status)) {
                cancel(status);
            }
            if (!toConsole) {
                status.update(this.totalCount, checkType.name(), dataCf.getCF().getName(), corruptedCount);
                persistStatus(status);
            }
            totalCorruptedCount += corruptedCount;
        }

        DbCheckerFileWriter.close();

        String msg = String.format("\nFinish to check DataObject records index: totally checked %d data CFs, "
                + "%d corrupted rows found.\n", resumeDataCfs.size(), totalCorruptedCount);

        helper.logMessage(msg, false, toConsole);

        return totalCorruptedCount;
    }

    /**
     * Scan all the indices and related data object records, to find out
     * the index record is existing but the related data object records is missing.
     *
     * @param toConsole whether print out in the console
     * @return the number of the corrupted rows in the index CFs
     * @throws ConnectionException
     */
    private int checkIndexObjects() throws ConnectionException {
        CheckType checkType = CheckType.INDEX_OBJECTS;
        helper.logMessage("\nStart to check INDEX data that the related object records are missing.\n", false, toConsole);

        DbConsistencyStatus status = getStatusFromZk();
        Collection<IndexAndCf> resumeIdxCfs = resumeFromWorkingPoint(checkType, status.getWorkingPoint());

        int totalCorruptCount = 0;
        for (IndexAndCf indexAndCf : resumeIdxCfs) {
            int corruptCount = helper.checkIndexingCF(indexAndCf, toConsole);
            status = getStatusFromZk();
            if (!toConsole && isCancelled(status)) {
                cancel(status);
            }
            if (!toConsole) {
                status.update(this.totalCount, checkType.name(), indexAndCf.generateKey(), corruptCount);
                persistStatus(status);
            }
            totalCorruptCount += corruptCount;
        }

        DbCheckerFileWriter.close();

        String msg = String.format("\nFinish to check INDEX records: totally checked %d indices " +
                "and %d corrupted rows found.\n", resumeIdxCfs.size(), totalCorruptCount);

        helper.logMessage(msg, false, toConsole);

        return totalCorruptCount;
    }

    private Collection resumeFromWorkingPoint(CheckType checkType, String workingPoint) {
        Collection sortedCfs;
        if (checkType == CheckType.INDEX_OBJECTS) {
            Collection<IndexAndCf> idxCfs = helper.getAllIndices().values();
            sortedCfs = sortIndexCfs(idxCfs);
        } else {
            // Currently, other cases are related to DataObjectType
            Collection<DataObjectType> allDoTypes = TypeMap.getAllDoTypes();
            sortedCfs = sortDataObjectCfs(allDoTypes);
        }

        if (toConsole || workingPoint == null) {
            return sortedCfs;
        }
        boolean found = false;
        List resumeCfs = new ArrayList<>();
        for (Object cfEntry : sortedCfs) {
            String cfWorkingPoint;
            if (checkType == CheckType.INDEX_OBJECTS) {
                IndexAndCf idxCf = (IndexAndCf) cfEntry;
                cfWorkingPoint = idxCf.generateKey();
            } else {
                DataObjectType dataCf = (DataObjectType) cfEntry;
                cfWorkingPoint = dataCf.getCF().getName();
            }

            if (workingPoint.equals(cfWorkingPoint)) {
                found = true;
            }
            if (found) {
                resumeCfs.add(cfEntry);
            }
        }
        return found ? resumeCfs : sortedCfs;
    }

    private Collection<DataObjectType> sortDataObjectCfs(Collection<DataObjectType> allDoTypes) {
        List<DataObjectType> types = new ArrayList<DataObjectType>(allDoTypes);
        Collections.sort(types, new Comparator<DataObjectType>() {

            @Override
            public int compare(DataObjectType type, DataObjectType anotherType) {
                return type.getCF().getName().compareTo(anotherType.getCF().getName());
            }
        });
        return Collections.unmodifiableCollection(types);
    }

    @SuppressWarnings("unchecked")
    private Collection<IndexAndCf> sortIndexCfs(Collection<IndexAndCf> idxCfs) {
        List<IndexAndCf> list = new ArrayList<IndexAndCf>(idxCfs);
        Collections.sort(list);
        return Collections.unmodifiableCollection(list);
    }

    private void cancel(DbConsistencyStatus status) {
        helper.logMessage("db consistency check is canceled", true, false);
        throw new CancellationException("db consistency has been cancelled");
    }

    private boolean isCancelled(DbConsistencyStatus status) {
        return status.isCancelled();
    }

    private enum CheckType {
        OBJECT_ID("OBJECT_INDICES"),
        OBJECT_INDICES("INDEX_OBJECTS"),
        INDEX_OBJECTS(null);
        
        private String next;
        CheckType(String next) {
            this.next = next;
        }
        
        public CheckType getNext() {
            return valueOf(next);
        }
        
        public boolean hasNext() {
            return this.next != null;
        }
    }
    
    private CheckType getCheckTypeFromZK() {
        CheckType defaultType = CheckType.OBJECT_ID;
        if (toConsole) {
            return defaultType;
        }
        DbConsistencyStatus status = getStatusFromZk();
        helper.logMessage(String.format("status %s in zk", status.toString()), false, false);
        CheckType checkType;
        try {
            checkType = CheckType.valueOf(status.getCheckType());
        } catch (Exception e) {
            checkType = defaultType;
        }
        return checkType;
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
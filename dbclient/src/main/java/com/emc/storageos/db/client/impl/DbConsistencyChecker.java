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
    private boolean toConsole;

    public DbConsistencyChecker() {
    }

    public DbConsistencyChecker(DbConsistencyCheckerHelper helper, boolean toConsole) {
        this.helper = helper;
        this.toConsole = toConsole;
    }

    public void check() throws ConnectionException {
        init();
        DbConsistencyStatus status = getStatusFromZk();
        helper.logMessage(String.format("status %s in zk", status.toString()), false, false);
        CheckType checkType = getCheckType(status.getCheckType());
        switch (checkType) {
            case OBJECT_ID:
                checkObjectId();
                setNextCheckType();
            case OBJECT_INDICES:
                checkObjectIndices();
                setNextCheckType();
            case INDEX_OBJECTS:
                checkIndexObjects();
        }

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
        int cfCount = TypeMap.getAllDoTypes().size();
        int indexCount = helper.getAllIndices().values().size();
        this.totalCount = indexCount + cfCount * 2;
        log.info(String.format("cfCount=%d indexCount=%d totalCount=%d", cfCount, indexCount, this.totalCount));
    }

    private void setNextCheckType() {
        DbConsistencyStatus status = getStatusFromZk();
        CheckType checkType = getCheckType(status.getCheckType());
        CheckType[] types = CheckType.values();
        if (checkType.ordinal() < types.length - 1) {
            CheckType nextType = types[checkType.ordinal() + 1];
            status.update(nextType.name(), null);
            persistStatus(status);
        }
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

        if (workingPoint == null) {
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

    /**
     * Find out all rows in DataObject CFs that can't be deserialized,
     * such as such as object id cannot be converted to URI.
     *
     * @return number of the corrupted rows in data CFs
     */
    private int checkObjectId() {
        CheckType checkType = CheckType.OBJECT_ID;
        DbConsistencyStatus status = getStatusFromZk();
        Collection<DataObjectType> resumeDataCfs = resumeFromWorkingPoint(checkType, status.getWorkingPoint());

        int totalDirtyCount = 0;
        int dirtyCount = 0;
        for (DataObjectType doType : resumeDataCfs) {
            helper.logMessage(String.format("processing %s cf", doType.getCF().getName()), false, false);
            if (!toConsole && isCancelled()) {
                cancel(status);
                return totalDirtyCount;
            }
            if (!toConsole) {
                //todo need to think inconsistencyCount argument is correct or not
                status.update(this.totalCount, CheckType.OBJECT_ID.name(), doType.getCF().getName(), dirtyCount);
                persistStatus(status);
            }

            dirtyCount = helper.checkDataObject(doType, toConsole);
            totalDirtyCount += dirtyCount;
        }

        String msg = String.format("\nTotally check %d cfs, %d rows are dirty.\n", resumeDataCfs.size(), totalDirtyCount);
        helper.logMessage(msg, false, toConsole);

        return totalDirtyCount;
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
        helper.logMessage("\nStart to check Data Object records that the related index is missing.\n", false, toConsole);

        DbConsistencyStatus status = getStatusFromZk();
        Collection<DataObjectType> resumeDataCfs = resumeFromWorkingPoint(checkType, status.getWorkingPoint());

        int totalCorruptedCount = 0;
        int corruptedCount = 0;
        for (DataObjectType doType : resumeDataCfs) {
            if (!toConsole && isCancelled()) {
                cancel(status);
                return totalCorruptedCount;
            }
            if (!toConsole) {
                status.update(this.totalCount, CheckType.OBJECT_INDICES.name(), doType.getCF().getName(), corruptedCount);
                persistStatus(status);
            }

            corruptedCount = helper.checkCFIndices(doType, toConsole);
            totalCorruptedCount += corruptedCount;
        }

        DbCheckerFileWriter.close();

        String msg = String.format(
                "\nFinish to check DataObject CFs: totally checked %d data CFs, "
                        + "%d corrupted rows found.",
                resumeDataCfs.size(), totalCorruptedCount);

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

        int corruptRowCount = 0;
        int totalCorruptCount = 0;

        for (IndexAndCf indexAndCf : resumeIdxCfs) {
            helper.logMessage(String.format("indexAndCf ", indexAndCf.generateKey()), false, toConsole);
            if (!toConsole && isCancelled()) {
                cancel(status);
                return totalCorruptCount;
            }

            if (!toConsole) {
                status.update(this.totalCount, CheckType.INDEX_OBJECTS.name(), indexAndCf.generateKey(), corruptRowCount);
                persistStatus(status);
            }
            corruptRowCount = helper.checkIndexingCF(indexAndCf, toConsole);
            totalCorruptCount += corruptRowCount;
        }

        DbCheckerFileWriter.close();

        String msg = String.format("\nFinish to check INDEX CFs: totally checked %d indices " +
                "and %d corrupted rows found.", resumeIdxCfs.size(), totalCorruptCount);

        helper.logMessage(msg, false, toConsole);

        return totalCorruptCount;
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
        status.movePreviousBack();
        persistStatus(status);
    }

    private boolean isCancelled() {
        DbConsistencyStatus status = getStatusFromZk();
        return status.isCancelled();
    }

    private enum CheckType {
        // This enum order determines the order of checking
        OBJECT_ID,
        OBJECT_INDICES,
        INDEX_OBJECTS,
    }

    private CheckType getCheckType(String type) {
        CheckType checkType;
        try {
            checkType = CheckType.valueOf(type);
        } catch (Exception e) {
            checkType = CheckType.OBJECT_ID;
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
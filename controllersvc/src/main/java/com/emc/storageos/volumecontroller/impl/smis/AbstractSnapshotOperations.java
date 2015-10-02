/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import java.net.URI;
import java.util.List;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.SnapshotOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisBlockCreateSnapshotJob;

/**
 * This class establishes a common, array-independent snapshot implementations.
 * 
 */
public abstract class AbstractSnapshotOperations implements SnapshotOperations {
    private static final Logger _log = LoggerFactory.getLogger(AbstractSnapshotOperations.class);
    protected DbClient _dbClient;
    protected SmisCommandHelper _helper;
    protected CIMObjectPathFactory _cimPath;
    protected NameGenerator _nameGenerator;

    public void setCimObjectPathFactory(CIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setSmisCommandHelper(SmisCommandHelper smisCommandHelper) {
        _helper = smisCommandHelper;
    }

    public void setNameGenerator(NameGenerator nameGenerator) {
        _nameGenerator = nameGenerator;
    }

    /**
     * Should implement creation of a single volume snapshot. That is a volume that
     * is not in any consistency group.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param createInactive - Indicates if the snapshots should be created but not
     *            activated
     * @param readOnly - Indicates if the snapshot should be read only.
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     */
    @Override
    public void createSingleVolumeSnapshot(StorageSystem storage, URI snapshot, Boolean createInactive, Boolean readOnly,
            TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            BlockSnapshot snapshotObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            _log.info("createSingleVolumeSnapshot operation START");
            Volume volume = _dbClient.queryObject(Volume.class, snapshotObj.getParent());
            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, volume.getTenant().getURI());
            String tenantName = tenant.getLabel();
            String snapLabelToUse =
                    _nameGenerator.generate(tenantName, snapshotObj.getLabel(),
                            snapshot.toString(), '-', SmisConstants.MAX_SNAPSHOT_NAME_LENGTH);
            CIMObjectPath replicationSvcPath = _cimPath.getControllerReplicationSvcPath(storage);
            CIMArgument[] inArgs = _helper.getCreateElementReplicaSnapInputArguments(storage, volume, createInactive, snapLabelToUse);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storage, replicationSvcPath, SmisConstants.CREATE_ELEMENT_REPLICA, inArgs, outArgs);
            CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
            if (job != null) {
                ControllerServiceImpl.enqueueJob(
                        new QueueJob(new SmisBlockCreateSnapshotJob(job,
                                storage.getId(), !createInactive, taskCompleter)));
            }
        } catch (Exception e) {
            _log.info("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, error);
            setInactive(snapshot, true);
        }
    }

    @Override
    public void copySnapshotToTarget(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter)
            throws DeviceControllerException {
        // Default: no implementation because not every array needs to support this
        // functionality
    }

    @Override
    public void copyGroupSnapshotsToTarget(StorageSystem storage,
            List<URI> snapshotList,
            TaskCompleter taskCompleter)
            throws DeviceControllerException {
        // Default: no implementation because not every array needs to support this
        // functionality
    }

    /**
     * Method for deactivating a single snapshot instance. To be used as a common utility.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param syncObjectPath [required] - The CIMObjectPath representing the block snapshot's
     *            SE_Synchronization object.
     * @throws Exception
     */
    protected void deactivateSnapshot(StorageSystem storage, BlockSnapshot snapshot, CIMObjectPath syncObjectPath)
            throws Exception {
        CIMInstance syncObject = _helper.getInstance(storage, syncObjectPath, false, false,
                new String[] { SmisConstants.EMC_COPY_STATE_DESC });
        String value = syncObject.getProperty(SmisConstants.EMC_COPY_STATE_DESC).getValue().toString();
        _log.info(String.format("Attempting to deactivate snapshot %s, EMCCopyStateDesc = %s",
                syncObjectPath.toString(), value));
        if (value.equalsIgnoreCase(SmisConstants.ACTIVE)) {
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.callModifyReplica(storage, _helper.getDeactivateSnapshotSynchronousInputArguments(syncObjectPath), outArgs);
            CIMProperty<CIMObjectPath> settingsData = (CIMProperty<CIMObjectPath>)
                    _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.CP_SETTINGS_STATE).
                            getKey(SmisConstants.CP_SETTING_DATA);
            String settingsInstance = settingsData.getValue().getKey(SmisConstants.CP_INSTANCE_ID).getValue().toString();
            snapshot.setSettingsInstance(settingsInstance);
            _dbClient.persistObject(snapshot);
        }
    }

    /**
     * Wrapper method will update the isSyncActive value of the snapshot object to the
     * 'isActive' value.
     * 
     * @param snapshot [required] - BlockSnapshot object to update
     * @param isActive [required] - Value to set
     */
    protected void setIsSyncActive(BlockSnapshot snapshot, boolean isActive) {
        try {
            snapshot.setIsSyncActive(isActive);
            _dbClient.persistObject(snapshot);
        } catch (DatabaseException e) {
            _log.error(
                    String.format("Caught an IOException when trying to set refreshInProgress parameter for snapshot %s",
                            snapshot.getLabel()), e);
        }
    }

    /**
     * Wrapper method will update the isActive value of the snapshot object to the
     * 'isActive' value.
     * 
     * @param snapshots [required] - List of BlockSnapshot objects to update
     * @param isActive [required] - Value to set
     */
    protected void setIsSyncActive(List<BlockSnapshot> snapshots, boolean isActive) {
        try {
            for (BlockSnapshot snapshot : snapshots) {
                snapshot.setIsSyncActive(isActive);
            }
            _dbClient.persistObject(snapshots);
        } catch (DatabaseException e) {
            _log.error("Caught an IOException when trying to set refreshInProgress snapshots", e);
        }
    }

    /**
     * Wrapper for setting the BlockSnapshot.inactive value
     * 
     * @param snapshotURI [in] - BlockSnapshot object to update
     * @param value [in] - Value to assign to inactive
     */
    protected void setInactive(URI snapshotURI, boolean value) {
        try {
            if (snapshotURI != null) {
                BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotURI);
                snapshot.setInactive(value);
                _dbClient.persistObject(snapshot);
            }
        } catch (DatabaseException e) {
            _log.error("IOException when trying to update snapshot.inactive value", e);
        }
    }

    /**
     * Wrapper for setting the BlockSnapshot.inactive value
     * 
     * @param snapshotURIs [in] - List of BlockSnapshot objects to update
     * @param value [in] - Value to assign to inactive
     */
    protected void setInactive(List<URI> snapshotURIs, boolean value) {
        try {
            if (snapshotURIs != null) {
                for (URI uri : snapshotURIs) {
                    BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, uri);
                    snapshot.setInactive(value);
                    _dbClient.persistObject(snapshot);
                }
            }
        } catch (DatabaseException e) {
            _log.error("IOException when trying to update snapshot.inactive value", e);
        }
    }

    @Override
    public void terminateAnyRestoreSessions(StorageSystem storage, BlockObject from, URI volume,
            TaskCompleter taskCompleter) throws Exception {
        // Default: no implementation because not every array needs to support this
        // functionality
    }
    @Override
    public void establishVolumeSnapshotGroupRelation(StorageSystem storage, URI sourceVolume,
            URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
    @Override
    public void resyncSingleVolumeSnapshot(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void resyncGroupSnapshots(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
}

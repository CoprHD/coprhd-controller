/*
* Copyright (c) 2016 EMC Corporation
* All Rights Reserved
*
* This software contains the intellectual property of EMC Corporation
* or is licensed to EMC Corporation from third parties.  Use of this
* software and the intellectual property contained therein is expressly
* limited to the terms and conditions of the License Agreement under which
* it is provided by or on behalf of EMC.
*/

package com.emc.storageos.driver.ibmsvcdriver.helpers;

import com.emc.storageos.driver.ibmsvcdriver.api.*;
import com.emc.storageos.driver.ibmsvcdriver.connection.Connection;
import com.emc.storageos.driver.ibmsvcdriver.connection.ConnectionManager;
import com.emc.storageos.driver.ibmsvcdriver.connection.SSHConnection;
import com.emc.storageos.driver.ibmsvcdriver.exceptions.IBMSVCDriverException;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCDriverTask;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCStorageDriver;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCConstants;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverConfiguration;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverUtils;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.*;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.SECONDS;

public class IBMSVCConsistencyGroups {

    private static final Logger _log = LoggerFactory.getLogger(IBMSVCStorageDriver.class);
    private IBMSVCDriverConfiguration ibmsvcdriverConfiguration = IBMSVCDriverConfiguration.getInstance();
    /*
     * Connection Manager for managing connection pool
     */
    private ConnectionManager connectionManager = null;

    /**
     * Constructor
     */
    public IBMSVCConsistencyGroups() {
        this.connectionManager = ConnectionManager.getInstance();
    }

    /**
     * Create driver task for task type
     *
     * @param taskType
     */
    public DriverTask createDriverTask(String taskType) {
        String taskID = String.format("%s+%s+%s", IBMSVCConstants.DRIVER_NAME, taskType, UUID.randomUUID());
        DriverTask task = new IBMSVCDriverTask(taskID);
        return task;
    }

    public DriverTask addVolumesToConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        // TODO Auto-generated method stub
        return null;
    }

    public DriverTask removeVolumesFromConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Create block consistency group.
     * 
     * @param consistencyGroup Type: input/output
     * @return task
     */
    public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_CREATE_FC_CONSISTGROUP);

        _log.info("createConsistencyGroup() for storage system {} - start", consistencyGroup.getStorageSystemId());

        // No Volume Consistency group in IBM SVC. Consistency group is for snapshot and clones only.
        // So we cannot create a consistency group and add volumes to it.
        // Also, we are creating separate consistency group during createConsistencyGroupSnapshot and clone operations
        // so skipping consistency group creation here
        // A unique ID needs to be set for consistency group though.

        String uniqueID = UUID.randomUUID().toString();

        consistencyGroup.setNativeId(uniqueID);
        consistencyGroup.setDeviceLabel(uniqueID);

        task.setStatus(DriverTask.TaskStatus.READY);

        _log.info("createConsistencyGroup() for storage system {} - end", consistencyGroup.getStorageSystemId());
        return task;
    }

    /**
     * Create Consistency Group on the Array
     * 
     * @param connection
     *            SSH Connection to the Array
     * @param consistencyGroupName
     *            Consistency Group Name
     * @param consistencyGroup
     *            Consistency Group Object. If null passed, a new object is created and returned
     * @return consistencyGroup
     * @throws IBMSVCDriverException
     */
    private VolumeConsistencyGroup createConsistencyGroup(SSHConnection connection, String consistencyGroupName,
            VolumeConsistencyGroup consistencyGroup) throws IBMSVCDriverException {

        if (consistencyGroup == null) {
            consistencyGroup = new VolumeConsistencyGroup();
        }
        IBMSVCCreateFCConsistGrpResult result = IBMSVCCLI.createFCConsistGrp(connection,
                consistencyGroupName);

        if (result.isSuccess()) {
            _log.info(String.format("Created flashCopy consistency group %s with Id %s.\n",
                    result.getConsistGrpName(), result.getConsistGrpId()));
            consistencyGroup.setNativeId(result.getConsistGrpId());
            consistencyGroup.setDisplayName(consistencyGroupName);
            consistencyGroup.setDeviceLabel(result.getConsistGrpName());
            return consistencyGroup;

        } else {
            throw new IBMSVCDriverException(String.format("Creating flashCopy consistency group %s failed %s\n",
                    result.getConsistGrpName(), result.getErrorString()));
        }

    }

    /**
     * Deleting the FC Consistency Group
     *
     * @param consistencyGroup
     *            Consistency Group to be deleted
     * @return task
     */
    public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
        // Since we are not creating volume consistency groups, delete consistency groups is not required.
        // Simply return task ready
        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DELETE_FC_CONSISTGROUP);
        task.setStatus(DriverTask.TaskStatus.READY);
        return task;
    }

    /**
     * Deleting the FC Consistency Group
     *
     * @param consistencyGroup
     *            Consistency Group to be deleted
     * @return task
     */
    public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup consistencyGroup, SSHConnection connection) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DELETE_FC_CONSISTGROUP);

        _log.info("deleteConsistencyGroup() for storage system {} - start", consistencyGroup.getStorageSystemId());

        try {
            if (connection == null) {
                connection = connectionManager.getClientBySystemId(consistencyGroup.getStorageSystemId());
            }

            IBMSVCDeleteFCConsistGrpResult result = deleteConsistencyGroup(connection, consistencyGroup);

            _log.info(String.format("Deleted flashCopy consistency group %s with Id %s.\n",
                    result.getConsistGrpName(), result.getConsistGrpId()));
            task.setMessage(String.format("Deleted flashCopy consistency group %s with Id %s.",
                    result.getConsistGrpName(), result.getConsistGrpId()));
            task.setStatus(DriverTask.TaskStatus.READY);

        } catch (Exception e) {
            _log.error("Unable to delete the flashCopy consistency group {} on the storage system {}",
                    consistencyGroup.getDeviceLabel(), consistencyGroup.getStorageSystemId());
            task.setMessage(
                    String.format("Unable to delete the flashCopy consistency group %s on the storage system %s",
                            consistencyGroup.getDeviceLabel(), consistencyGroup.getStorageSystemId()) + e.getMessage());
            task.setStatus(DriverTask.TaskStatus.FAILED);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        _log.info("deleteConsistencyGroup() for storage system {} - end", consistencyGroup.getStorageSystemId());
        return task;
    }

    /**
     * Delete Consistency Group
     * 
     * @param connection
     *            SSH Connection to Array
     * @param consistencyGroup
     *            Consistency Group object
     * @throws IBMSVCDriverException
     */
    private IBMSVCDeleteFCConsistGrpResult deleteConsistencyGroup(SSHConnection connection, VolumeConsistencyGroup consistencyGroup)
            throws IBMSVCDriverException {

        IBMSVCDeleteFCConsistGrpResult result = IBMSVCCLI.deleteFCConsistGrp(connection,
                consistencyGroup.getNativeId(), consistencyGroup.getDeviceLabel());

        if (!result.isSuccess()) {
            throw new IBMSVCDriverException(String.format("Deleting flashCopy consistency group %s failed %s\n",
                    result.getConsistGrpName(), result.getErrorString()));
        }

        return result;

    }

    /**
     * Create the consistency group snapshot volume
     *
     * @param consistencyGroup
     *            Consistency Group to create snapshot
     * @param snapshots
     *            List of Volume Snapshots
     * @param capabilities
     *            Capabilities
     * @return
     */
    public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup,
            List<VolumeSnapshot> snapshots, List<CapabilityInstance> capabilities) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_CREATE_FC_CONSISTGROUP_SNAPSHOT);

        List<IBMSVCCreateVolumeResult> listOfCreatedVolumes = new ArrayList<>();

        SSHConnection connection = null;
        VolumeConsistencyGroup newConsistencyGroup = null;

        try {
            _log.info("createConsistencyGroupSnapshot() for storage system {} - start",
                    snapshots.get(0).getStorageSystemId());

            connection = connectionManager.getClientBySystemId(snapshots.get(0).getStorageSystemId());

            String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String newConsistencyGroupName = consistencyGroup.getDisplayName() + "_" + timeStamp;

            // Create a new Consistency Group for each CG Snapshot/replication operation.
            newConsistencyGroup = createConsistencyGroup(connection, newConsistencyGroupName, null);

            String consistencyGrpId = newConsistencyGroup.getNativeId();
            // Use displayName or deviceLabel? deviceLabel is being passed in as null currently hence using displayName
            String consistencyGrpName = newConsistencyGroup.getDisplayName();

            for (VolumeSnapshot volumeSnapshot : snapshots) {

                IBMSVCFlashCopy.createFlashCopy(connection, volumeSnapshot.getStorageSystemId(), volumeSnapshot.getParentId(),
                        volumeSnapshot, consistencyGrpId, false, false, listOfCreatedVolumes);

            }

            // -------------------------------------------------------------------------
            // Start FCMap copy for Consistency Groups

            // Start FC Consistency Group with prep option
            startFCConsistGrp(connection, consistencyGrpId, consistencyGrpName, false, true);

            task.setMessage(String.format(
                    "Created flashCopy consistency group %s and added flashCopy mappings",
                    consistencyGrpName));

            task.setStatus(DriverTask.TaskStatus.READY);

        } catch (Exception e) {
            _log.error("Unable to create consistency group snapshot volumes on the storage system {} - {}",
                    consistencyGroup.getStorageSystemId(), e.getMessage());
            task.setMessage(
                    String.format("Unable to create consistency group snapshot volumes on the storage system %s",
                            consistencyGroup.getStorageSystemId()) + e.getMessage());

            /**
             * Deleting volume stops and deletes all
             * the related FC mappings to that
             * volume And finally deletes the volume
             */
            IBMSVCFlashCopy.cleanupMappingsAndVolumes(connection, listOfCreatedVolumes);

            // Delete Consistency Group
            deleteConsistencyGroup(newConsistencyGroup, connection);

            task.setStatus(DriverTask.TaskStatus.FAILED);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        _log.info("createConsistencyGroupSnapshot() for storage system {} - end",
                snapshots.get(0).getStorageSystemId());

        return task;
    }

    /**
     * Restore volume to snapshot state.
     *
     * @param snapshots
     *            Type: Input/Output.
     * @return task
     */
    public DriverTask restoreConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_RESTORE_SNAPSHOT_VOLUMES);

        _log.info("restoreConsistencyGroupSnapshot() for storage system {} - start",
                snapshots.get(0).getStorageSystemId());

        SSHConnection connection = null;
        VolumeConsistencyGroup newConsistencyGroup = null;

        List<IBMSVCCreateFCMappingResult> listOfCreatedMaps = new ArrayList<>();

        // This method will only be called after it is verified that all snapshots belong to a single Consistency group
        // So it is safe to get value like that
        String consistencyGroupID = snapshots.get(0).getConsistencyGroup();

        try {

            connection = connectionManager.getClientBySystemId(snapshots.get(0).getStorageSystemId());

            // Get current consistency group name
            IBMSVCQueryFCConsistGrpResult resultConsistGrpQuery = IBMSVCCLI.queryFCConsistGrp(connection,
                    consistencyGroupID, null);
            if (!resultConsistGrpQuery.isSuccess()) {
                throw new IBMSVCDriverException(resultConsistGrpQuery.getErrorString());
            }

            // Generate new consistency group name for restore operation
            String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String newConsistencyGroupName = resultConsistGrpQuery.getConsistGrpName() + "_" + timeStamp + "_restore";

            // Create a new Consistency Group for each CG Snapshot/replication operation.
            newConsistencyGroup = createConsistencyGroup(connection, newConsistencyGroupName, null);

            String consistencyGrpId = newConsistencyGroup.getNativeId();
            // Use displayName or deviceLabel? deviceLabel is being passed in as null currently hence using displayName
            String consistencyGrpName = newConsistencyGroup.getDisplayName();

            for (VolumeSnapshot volumeSnapshot : snapshots) {

                IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCFlashCopy.createFlashCopy(connection, volumeSnapshot.getStorageSystemId(), volumeSnapshot.getParentId(),
                        volumeSnapshot, consistencyGrpId, false, true, null);

                listOfCreatedMaps.add(resultFCMapping);

            }

            // -------------------------------------------------------------------------
            // Start FCMap copy for Consistency Groups

            // Start FC Consistency Group with prep option
            startFCConsistGrp(connection, consistencyGrpId, consistencyGrpName, true, true);

            task.setMessage(String.format(
                    "Created flashCopy consistency group %s and added flashCopy mappings",
                    consistencyGrpName));

            task.setStatus(DriverTask.TaskStatus.READY);

        } catch (Exception e) {
            _log.error("Unable to restore consistency group snapshot volumes on the storage system {} - {}",
                    snapshots.get(0).getStorageSystemId(), e.getMessage());
            task.setMessage(
                    String.format("Unable to restore consistency group snapshot volumes on the storage system %s",
                            snapshots.get(0).getStorageSystemId()) + e.getMessage());

            // Cleanup created Mappings
            IBMSVCFlashCopy.cleanupMappings(connection, listOfCreatedMaps);

            // Delete Consistency Group
            deleteConsistencyGroup(newConsistencyGroup, connection);

            task.setStatus(DriverTask.TaskStatus.FAILED);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        _log.info("restoreConsistencyGroupSnapshot() for storage system {} - end",
                snapshots.get(0).getStorageSystemId());

        return task;

    }

    /**
     * Delete the consistency group snapshot volume
     *
     * @param snapshots
     * @return
     */
    public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DELETE_FC_CONSISTGROUP_SNAPSHOT);

        for (VolumeSnapshot volumeSnapshot : snapshots) {

            _log.info("deleteConsistencyGroupSnapshot() for storage system {} ; CG - {} - start",
                    volumeSnapshot.getStorageSystemId(), volumeSnapshot.getConsistencyGroup());
            SSHConnection connection = null;
            try {
                connection = connectionManager.getClientBySystemId(volumeSnapshot.getStorageSystemId());

                _log.info(String.format("Deleting the snapshot volume Id %s\n", volumeSnapshot.getNativeId()));

                // Delete the Snapshot Volume
                IBMSVCDeleteVolumeResult resultDelVol = IBMSVCCLI.deleteStorageVolumes(connection,
                        volumeSnapshot.getNativeId());

                if (resultDelVol.isSuccess()) {
                    _log.info(String.format("Deleted snapshot volume Id %s.\n", resultDelVol.getId()));
                    task.setMessage(String.format("Snapshot volume Id %s has been deleted.", resultDelVol.getId()));
                    task.setStatus(DriverTask.TaskStatus.READY);
                } else {
                    _log.error(String.format("Deleting snapshot volume Id %s failed : %s\n", resultDelVol.getId(),
                            resultDelVol.getErrorString()), resultDelVol.isSuccess());
                    task.setMessage(String.format("Deleting snapshot volume Id %s failed : %s", resultDelVol.getId(),
                            resultDelVol.getErrorString()));
                    task.setStatus(DriverTask.TaskStatus.FAILED);
                    break;
                }

            } catch (Exception e) {
                _log.error("Unable to delete the snapshot volume {} on the storage system {}",
                        volumeSnapshot.getParentId(), volumeSnapshot.getStorageSystemId());
                task.setMessage(String.format("Unable to delete the snapshot volume %s on the storage system %s",
                        volumeSnapshot.getDeviceLabel(), volumeSnapshot.getStorageSystemId()) + e.getMessage());
                task.setStatus(DriverTask.TaskStatus.FAILED);
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            _log.info("deleteConsistencyGroupSnapshot() for storage system {} - end",
                    volumeSnapshot.getStorageSystemId());
        }
        return task;
    }

    /**
     * Create the consistency group clone volume
     *
     * @param consistencyGroup
     * @param clones
     * @param capabilities
     * @return
     */
    public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones,
            List<CapabilityInstance> capabilities) {
        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_CREATE_FC_CONSISTGROUP_SNAPSHOT);

        List<IBMSVCCreateVolumeResult> listOfCreatedVolumes = new ArrayList<>();
        List<IBMSVCCreateFCMappingResult> listOfCreatedMaps = new ArrayList<>();

        SSHConnection connection = null;
        VolumeConsistencyGroup newConsistencyGroup = null;

        try {
            _log.info("createConsistencyGroupClone() for storage system {} - start",
                    clones.get(0).getStorageSystemId());

            connection = connectionManager.getClientBySystemId(clones.get(0).getStorageSystemId());

            String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String newConsistencyGroupName = consistencyGroup.getDisplayName() + "_" + timeStamp;

            // Create a new Consistency Group for each CG Snapshot/replication operation.
            newConsistencyGroup = createConsistencyGroup(connection, newConsistencyGroupName, null);

            String consistencyGrpId = newConsistencyGroup.getNativeId();
            // Use displayName or deviceLabel? deviceLabel is being passed in as null currently hence using displayName
            String consistencyGrpName = newConsistencyGroup.getDisplayName();

            for (VolumeClone volumeClone : clones) {

                IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCFlashCopy.createFlashCopy(connection, volumeClone.getStorageSystemId(), volumeClone.getParentId(),
                        volumeClone, consistencyGrpId, true, false, listOfCreatedVolumes);

                volumeClone.setReplicationState(VolumeClone.ReplicationState.CREATED);

            }

            // -------------------------------------------------------------------------
            // Start FCMap copy for Consistency Groups

            // Start FC Consistency Group with prep option
            startFCConsistGrp(connection, consistencyGrpId, consistencyGrpName, false, true);

            task.setMessage(String.format(
                    "Created flashCopy consistency group %s and added flashCopy mappings",
                    consistencyGrpName));

            IBMSVCFlashCopy.waitForFCMapState(clones, listOfCreatedMaps.get(0).getId());

            task.setStatus(DriverTask.TaskStatus.READY);


        } catch (Exception e) {
            _log.error("Unable to create consistency group clone volumes on the storage system {} - {}",
                    consistencyGroup.getStorageSystemId(), e.getMessage());
            task.setMessage(
                    String.format("Unable to create consistency group clone volumes on the storage system %s",
                            consistencyGroup.getStorageSystemId()) + e.getMessage());

            /**
             * Deleting volume stops and deletes all
             * the related FC mappings to that
             * volume And finally deletes the volume
             */
            IBMSVCFlashCopy.cleanupMappingsAndVolumes(connection, listOfCreatedVolumes);

            // Delete Consistency Group
            deleteConsistencyGroup(newConsistencyGroup, connection);

            task.setStatus(DriverTask.TaskStatus.FAILED);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        _log.info("createConsistencyGroupClone() for storage system {} - end",
                clones.get(0).getStorageSystemId());

        return task;
    }

    /**
     * Restore volume to clone state.
     *
     * @param clones
     *            Type: Input/Output.
     * @return task
     */
    public DriverTask restoreConsistencyGroupClone(List<VolumeClone> clones) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_RESTORE_SNAPSHOT_VOLUMES);

        _log.info("restoreConsistencyGroupClone() for storage system {} - start",
                clones.get(0).getStorageSystemId());

        SSHConnection connection = null;
        VolumeConsistencyGroup newConsistencyGroup = null;

        List<IBMSVCCreateFCMappingResult> listOfCreatedMaps = new ArrayList<>();

        // This method will only be called after it is verified that all snapshots belong to a single Consistency group
        // So it is safe to get value like that
        String consistencyGroupID = clones.get(0).getConsistencyGroup();

        try {

            connection = connectionManager.getClientBySystemId(clones.get(0).getStorageSystemId());

            // Get current consistency group name
            IBMSVCQueryFCConsistGrpResult resultConsistGrpQuery = IBMSVCCLI.queryFCConsistGrp(connection,
                    consistencyGroupID, null);
            if (!resultConsistGrpQuery.isSuccess()) {
                throw new IBMSVCDriverException(resultConsistGrpQuery.getErrorString());
            }

            // Generate new consistency group name for restore operation
            String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String newConsistencyGroupName = resultConsistGrpQuery.getConsistGrpName() + "_" + timeStamp + "_restore";

            // Create a new Consistency Group for each CG Snapshot/replication operation.
            newConsistencyGroup = createConsistencyGroup(connection, newConsistencyGroupName, null);

            String consistencyGrpId = newConsistencyGroup.getNativeId();
            // Use displayName or deviceLabel? deviceLabel is being passed in as null currently hence using displayName
            String consistencyGrpName = newConsistencyGroup.getDisplayName();

            for (VolumeClone volumeClone : clones) {

                IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCFlashCopy.createFlashCopy(connection, volumeClone.getStorageSystemId(), volumeClone.getParentId(),
                        volumeClone, consistencyGrpId, true, true, null);

                listOfCreatedMaps.add(resultFCMapping);

            }

            // -------------------------------------------------------------------------
            // Start FCMap copy for Consistency Groups

            // Start FC Consistency Group with prep option
            startFCConsistGrp(connection, consistencyGrpId, consistencyGrpName, true, true);

            task.setMessage(String.format(
                    "Created flashCopy consistency group %s and added flashCopy mappings",
                    consistencyGrpName));

            IBMSVCFlashCopy.waitForFCMapState(clones, listOfCreatedMaps.get(0).getId());

            task.setStatus(DriverTask.TaskStatus.READY);

        } catch (Exception e) {
            _log.error("Unable to restore consistency group clone volumes on the storage system {} - {}",
                    clones.get(0).getStorageSystemId(), e.getMessage());
            task.setMessage(
                    String.format("Unable to restore consistency group clone volumes on the storage system %s",
                            clones.get(0).getStorageSystemId()) + e.getMessage());

            // Cleanup created Mappings
            IBMSVCFlashCopy.cleanupMappings(connection, listOfCreatedMaps);

            // Delete Consistency Group
            deleteConsistencyGroup(newConsistencyGroup, connection);

            task.setStatus(DriverTask.TaskStatus.FAILED);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        _log.info("restoreConsistencyGroupClone() for storage system {} - end",
                clones.get(0).getStorageSystemId());

        return task;

    }

    /**
     * Delete the consistency group clone volume
     *
     * @param clones
     * @return
     */
    public DriverTask deleteConsistencyGroupClone(List<VolumeClone> clones) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DELETE_FC_CONSISTGROUP_CLONE);

        for (VolumeClone volumeClone : clones) {

            _log.info("deleteConsistencyGroupSnapshot() for storage system {} - start",
                    volumeClone.getStorageSystemId());
            SSHConnection connection = null;
            try {
                connection = connectionManager.getClientBySystemId(volumeClone.getStorageSystemId());

                _log.info(String.format("Deleting the clone volume Id %s\n", volumeClone.getNativeId()));

                // Delete the Snapshot Volume
                IBMSVCDeleteVolumeResult resultDelVol = IBMSVCCLI.deleteStorageVolumes(connection,
                        volumeClone.getNativeId());

                if (resultDelVol.isSuccess()) {
                    _log.info(String.format("Deleted clone volume Id %s.\n", resultDelVol.getId()));
                    task.setMessage(String.format("Clone volume Id %s has been deleted.", resultDelVol.getId()));
                    task.setStatus(DriverTask.TaskStatus.READY);
                } else {
                    _log.error(String.format("Deleting clone volume Id %s failed : %s\n", resultDelVol.getId(),
                            resultDelVol.getErrorString()), resultDelVol.isSuccess());
                    task.setMessage(String.format("Deleting clone volume Id %s failed : %s", resultDelVol.getId(),
                            resultDelVol.getErrorString()));
                    task.setStatus(DriverTask.TaskStatus.FAILED);
                    break;
                }

            } catch (Exception e) {
                _log.error("Unable to delete the clone volume {} on the storage system {}", volumeClone.getParentId(),
                        volumeClone.getStorageSystemId());
                task.setMessage(String.format("Unable to delete the clone volume %s on the storage system %s",
                        volumeClone.getDeviceLabel(), volumeClone.getStorageSystemId()) + e.getMessage());
                task.setStatus(DriverTask.TaskStatus.FAILED);
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            _log.info("deleteConsistencyGroupSnapshot() for storage system {} - end", volumeClone.getStorageSystemId());
        }
        return task;
    }

    /**
     * Prepare FC Consistency Group of Mappings
     *
     * @param connection
     * @param fcConsistGrpId
     * @param fcConsistGrpName
     */
    private void preStartFCMConsistGrp(SSHConnection connection, String fcConsistGrpId, String fcConsistGrpName) {
        IBMSVCPreStartFCConsistGrpResult resultPreStartFCConsistGrp = IBMSVCCLI.preStartFCConsistGrp(connection,
                fcConsistGrpId, fcConsistGrpName);
        if (resultPreStartFCConsistGrp.isSuccess()) {
            _log.info(String.format("Prepared to start flashCopy consistency group %s of mappings.\n",
                    resultPreStartFCConsistGrp.getConsistGrpName()));
        } else {
            _log.warn(String.format("Preparing to start flashCopy consistency group %s of mappings failed : %s.\n",
                    resultPreStartFCConsistGrp.getConsistGrpName(), resultPreStartFCConsistGrp.getErrorString()));
        }
    }

    /**
     * Start FC Consistency Group of Mappings
     *
     * @param connection
     * @param fcConsistGrpId
     * @param fcConsistGrpName
     */
    private void startFCConsistGrp(SSHConnection connection, String fcConsistGrpId, String fcConsistGrpName, boolean restore, boolean prep)
            throws Exception {
        IBMSVCStartFCConsistGrpResult resultStartFCConsistGrp = IBMSVCCLI.startFCConsistGrp(connection, fcConsistGrpId,
                fcConsistGrpName, restore, prep);
        if (resultStartFCConsistGrp.isSuccess()) {
            _log.info(String.format("Started flashCopy consistency group %s of mappings.\n",
                    resultStartFCConsistGrp.getConsistGrpName()));
        } else {
            _log.warn(String.format("Starting flashCopy consistency group %s of mappings failed : %s.\n",
                    resultStartFCConsistGrp.getConsistGrpName(), resultStartFCConsistGrp.getErrorString()));

            throw new IBMSVCDriverException(String.format("Starting flashCopy consistency group %s of mappings failed : %s.\n",
                    resultStartFCConsistGrp.getConsistGrpName(), resultStartFCConsistGrp.getErrorString()));
        }
    }

    /**
     * Stopping FC Consistency Group of Mappings
     *
     * @param connection
     * @param fcConsistGrpId
     * @param fcConsistGrpName
     */
    private void stopFCConsistGrp(SSHConnection connection, String fcConsistGrpId, String fcConsistGrpName) {
        // Remove the FC Mapping and delete the snapshot volume
        IBMSVCStopFCConsistGrpResult resultStopFCConsistGrp = IBMSVCCLI.stopFCConsistGrp(connection, fcConsistGrpId,
                fcConsistGrpName);
        if (resultStopFCConsistGrp.isSuccess()) {
            _log.info(String.format("Stopped flashCopy consistency group %s of mappings.\n",
                    resultStopFCConsistGrp.getConsistGrpName()));
        } else {
            _log.warn(String.format("Stopping flashCopy consistency group %s of mappings failed : %s.\n",
                    resultStopFCConsistGrp.getConsistGrpName(), resultStopFCConsistGrp.getErrorString()));
        }
    }

}

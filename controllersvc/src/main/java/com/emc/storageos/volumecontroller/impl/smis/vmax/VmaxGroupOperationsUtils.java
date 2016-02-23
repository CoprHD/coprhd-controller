/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.vmax;

import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CREATE_GROUP_REPLICA;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.DELETE_GROUP;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.JOB;
import static java.text.MessageFormat.format;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants.SYNC_TYPE;
import com.emc.storageos.volumecontroller.impl.smis.SmisException;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;
import com.emc.storageos.volumecontroller.impl.utils.ConsistencyUtils;

/**
 * Utils for consistency group related operations for snapshot and clone
 * 
 */
public class VmaxGroupOperationsUtils {
    private static final Logger _log = LoggerFactory.getLogger(VmaxGroupOperationsUtils.class);

    /**
     * This will create the replica using the target group
     * 
     * @param storage - StorageSystem where the snapshot will be done
     * @param groupName - Name of the source group
     * @param replicaLabel - Label for the replicas
     * @param targetGroupPath - TargetGroup where the snaps will be placed
     * @param createInactive - whether the snapshot needs to to be created with sync_active=true/false
     * @param taskCompleter - Completer object used for task status update
     * @param syncType - sync type, e.g. snapshot or clone
     * @param dbClient
     * @param helper - smisCommandHelper
     * @param cimPath - CIMObjectPathFactory
     * @throws DeviceControllerException
     */
    public static CIMObjectPath internalCreateGroupReplica(StorageSystem storage, String groupName,
            String replicaLabel,
            CIMObjectPath targetGroupPath,
            boolean createInactive,
            TaskCompleter taskCompleter,
            SYNC_TYPE syncType,
            DbClient dbClient,
            SmisCommandHelper helper,
            CIMObjectPathFactory cimPath) throws DeviceControllerException {
        return internalCreateGroupReplica(storage, groupName, replicaLabel, targetGroupPath, createInactive, false,
                taskCompleter, syncType, dbClient, helper, cimPath);
    }

    public static CIMObjectPath internalCreateGroupReplica(StorageSystem storage, String groupName,
            String replicaLabel,
            CIMObjectPath targetGroupPath,
            boolean createInactive, boolean thinProvisioning,
            TaskCompleter taskCompleter,
            SYNC_TYPE syncType,
            DbClient dbClient,
            SmisCommandHelper helper,
            CIMObjectPathFactory cimPath) throws DeviceControllerException {
        CIMObjectPath job = null;
        try {
            CIMObjectPath cgPath = cimPath.getReplicationGroupPath(storage, groupName);
            CIMObjectPath replicationSvc = cimPath.getControllerReplicationSvcPath(storage);
            CIMInstance replicaSettingData = null;
            if (syncType == SYNC_TYPE.CLONE && storage.checkIfVmax3() && ControllerUtils.isVmaxUsing81SMIS(storage, dbClient)) {
                /**
                 * VMAX3 using SMI 8.1 provider needs to send DesiredCopyMethodology=32770
                 * to create TimeFinder differential clone.
                 */
                replicaSettingData = ReplicationUtils.getReplicationSettingForSMIS81TFGroupClones(storage, helper,
                        cimPath, createInactive);
            } else if (storage.checkIfVmax3() && syncType != SYNC_TYPE.MIRROR) {
                String instanceId = targetGroupPath.getKey(SmisConstants.CP_INSTANCE_ID).getValue().toString();
                replicaLabel = SmisUtils.getTargetGroupName(instanceId, storage.getUsingSmis80());
                // Unlike single volume snapshot where snapSettingName is used in SMI-S as StorageSynchronized.EMCRelationshipName,
                // for group snapshot, GroupSynchronized.RelationshipName is set via RelationshipName input argument of CreateGroupReplica
                // method.
                // Using replicaLabel in the call below is not necessary, just for convenience.
                // But it has to be used in the getCreateGroupReplicaInputArgumentsForVMAX below.
                replicaSettingData = helper.getReplicationSettingData(storage, replicaLabel, true);
            } else if (syncType == SYNC_TYPE.CLONE) {
                replicaSettingData = ReplicationUtils.getReplicationSettingForGroupClones(storage, helper,
                        cimPath, createInactive);
            } else if (syncType == SYNC_TYPE.MIRROR) {
                replicaSettingData = ReplicationUtils.getReplicationSettingForGroupMirrors(storage, helper, cimPath);
            } else {
                replicaSettingData = ReplicationUtils.getReplicationSettingForGroupSnapshots(storage, helper, cimPath, thinProvisioning);
            }

            CIMArgument[] inArgs = helper.getCreateGroupReplicaInputArgumentsForVMAX(storage, cgPath, createInactive, replicaLabel,
                    targetGroupPath,
                    replicaSettingData, syncType);
            CIMArgument[] outArgs = new CIMArgument[5];
            helper.invokeMethod(storage, replicationSvc, CREATE_GROUP_REPLICA, inArgs, outArgs);
            job = cimPath.getCimObjectPathFromOutputArgs(outArgs, JOB);

        } catch (Exception e) {
            _log.info("Problem making SMI-S call: ", e);
            // setInactive(((BlockSnapshotCreateCompleter)taskCompleter).getSnapshotURIs(), true);
            taskCompleter.error(dbClient, SmisException.errors.methodFailed(CREATE_GROUP_REPLICA, e.getMessage()));
            throw new SmisException("Error when creating group replica", e);
        }
        return job;
    }

    /**
     * Deletes a target group represented by the given target group path
     * 
     * @param storage - StorageSystem where the target group is
     * @param targetGroupPath - Path representing target group to be deleted
     * 
     * @throws DeviceControllerException
     */
    public static void deleteTargetDeviceGroup(final StorageSystem storage, final CIMObjectPath targetGroupPath,
            final DbClient dbClient, final SmisCommandHelper helper, final CIMObjectPathFactory cimPath) {

        _log.info(format("Removing target device group {0} from storage system {1}", targetGroupPath, storage.getId()));

        try {
            CIMObjectPath replicationSvc = cimPath.getControllerReplicationSvcPath(storage);
            CIMArgument[] outArgs = new CIMArgument[5];
            CIMArgument[] inArgs = helper.getDeleteReplicationGroupInputArguments(storage, targetGroupPath, true);

            helper.invokeMethod(storage, replicationSvc, DELETE_GROUP, inArgs, outArgs);
        } catch (Exception e) {
            _log.error(
                    format("An error occurred when removing target device group {0} from storage system {1}", targetGroupPath,
                            storage.getId()), e);
        }
    }

    /**
     * This interface is for the group replica active. The created replica may have done
     * whatever is necessary to setup the replic for this call. The goal is to
     * make this a quick operation and the create operation has already done a lot
     * of the "heavy lifting".
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param sourceVolume [required] - source of the replication
     * @param blockObj [required] - target of the replication
     * @param syncType [required] - either snapshot, or clone
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @return isSuccess - true: the operation is successful; false: the operation fails
     * 
     */
    public static boolean activateGroupReplicas(final StorageSystem storage,
            final BlockObject sourceVolume,
            final BlockObject blockObj,
            final SYNC_TYPE syncType,
            final TaskCompleter taskCompleter,
            final DbClient dbClient,
            final SmisCommandHelper helper,
            final CIMObjectPathFactory cimPath) throws Exception {
        boolean isSuccess = false;
        String groupName = ConsistencyUtils.getSourceConsistencyGroupName(sourceVolume, dbClient);
        String replicaGroupName = blockObj.getReplicationGroupInstance();
        CIMObjectPath groupSynchronized = cimPath.getGroupSynchronizedPath(storage, groupName, replicaGroupName);
        CIMArgument[] inArgs = null;
        if (helper.checkExists(storage, groupSynchronized, false, false) != null) {
            if (syncType == SYNC_TYPE.SNAPSHOT) {
                inArgs = helper.getActivateGroupSnapshotInputArguments(storage, groupSynchronized);
            } else if (syncType == SYNC_TYPE.CLONE) {
                inArgs = helper.getActivateGroupFullCopyInputArguments(storage, groupSynchronized);
            }
            CIMArgument[] outArgs = new CIMArgument[5];
            helper.callModifyReplica(storage, inArgs, outArgs);
            isSuccess = true;
        } else {
            ServiceError error = DeviceControllerErrors.smis.unableToFindSynchPath(groupName);
            taskCompleter.error(dbClient, error);
        }
        return isSuccess;

    }
}

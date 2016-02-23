/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_REPLICATION_GROUP;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CREATE_GROUP;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CREATE_NEW_TARGET_VALUE;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.DEFAULT_INSTANCE;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.DELETE_GROUP;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.DESIRED_COPY_METHODOLOGY;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.EMC_RETURN_TO_STORAGE_POOL;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.GET_DEFAULT_REPLICATION_SETTING_DATA;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.MIRROR_REPLICATION_TYPE;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.RETURN_ELEMENTS_TO_STORAGE_POOL;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.SNAPSHOT_REPLICATION_TYPE;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.TARGET_ELEMENT_SUPPLIER;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.VP_SNAP_VALUE;
import static java.text.MessageFormat.format;
import static javax.cim.CIMDataType.UINT16_T;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.cim.CIMArgument;
import javax.cim.CIMDataType;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.cim.UnsignedInteger16;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants.SYNC_TYPE;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCreateVmaxCGTargetVolumesJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisDeleteVmaxCGTargetVolumesJob;
import com.emc.storageos.volumecontroller.impl.utils.ConsistencyUtils;
import com.google.common.base.Joiner;

/**
 * Class to contain common utilities for Replication related operations
 */
public class ReplicationUtils {
    private static final Logger _log = LoggerFactory.getLogger(ReplicationUtils.class);

    public static class ReplicationSettingBuilder {
        private final StorageSystem storage;
        private final SmisCommandHelper helper;
        private final CIMObjectPathFactory cimPath;

        private final int replicationType;

        private final Set<CIMProperty> properties = new HashSet<>();

        public ReplicationSettingBuilder(StorageSystem storage, SmisCommandHelper helper, CIMObjectPathFactory cimPath) {
            this(storage, helper, cimPath, SNAPSHOT_REPLICATION_TYPE);
        }

        public ReplicationSettingBuilder(StorageSystem storage, SmisCommandHelper helper, CIMObjectPathFactory cimPath, int replicationType) {
            this.storage = storage;
            this.helper = helper;
            this.cimPath = cimPath;
            this.replicationType = replicationType;
        }

        public CIMInstance build() throws WBEMException {
            CIMObjectPath repCapabilities = cimPath.getReplicationServiceCapabilitiesPath(storage);
            CIMArgument[] repSettingInArgs = helper.getReplicationSettingDataInstance(replicationType);
            CIMArgument[] repSettingOutArgs = new CIMArgument[5];

            helper.invokeMethod(storage, repCapabilities, GET_DEFAULT_REPLICATION_SETTING_DATA, repSettingInArgs,
                    repSettingOutArgs);

            CIMInstance modifiedInstance = (CIMInstance) cimPath.getFromOutputArgs(repSettingOutArgs, DEFAULT_INSTANCE);

            return modifiedInstance.deriveInstance(properties.toArray(new CIMProperty[] {}));
        }

        public ReplicationSettingBuilder addVPSnap() {
            return addProperty(new CIMProperty<Object>(DESIRED_COPY_METHODOLOGY, UINT16_T,
                    new UnsignedInteger16(VP_SNAP_VALUE)));
        }

        public ReplicationSettingBuilder addCreateNewTarget() {
            return addProperty(new CIMProperty<Object>(TARGET_ELEMENT_SUPPLIER, UINT16_T,
                    new UnsignedInteger16(CREATE_NEW_TARGET_VALUE)));
        }

        public ReplicationSettingBuilder addCopyBeforeActivate() {
            return addProperty(new CIMProperty<Object>(SmisConstants.DESIRED_COPY_METHODOLOGY, UINT16_T,
                    new UnsignedInteger16(SmisConstants.COPY_BEFORE_ACTIVATE)));
        }

        public ReplicationSettingBuilder addDifferentialClone() {
            return addProperty(new CIMProperty<Object>(SmisConstants.DESIRED_COPY_METHODOLOGY, UINT16_T,
                    new UnsignedInteger16(SmisConstants.DIFFERENTIAL_CLONE_VALUE)));
        }

        public ReplicationSettingBuilder addSMIS81TFDifferentialClone() {
            return addProperty(new CIMProperty<Object>(SmisConstants.DESIRED_COPY_METHODOLOGY, UINT16_T,
                    new UnsignedInteger16(SmisConstants.SMIS810_TF_DIFFERENTIAL_CLONE_VALUE)));
        }

        public ReplicationSettingBuilder addConsistentPointInTime() {
            return addProperty(new CIMProperty<Object>(SmisConstants.CP_CONSISTENT_POINT_IN_TIME,
                    CIMDataType.BOOLEAN_T, true));
        }

        private ReplicationSettingBuilder addProperty(CIMProperty property) {
            properties.add(property);
            return this;
        }
    }

    /**
     * This method will conditionally invoke the SmisCommandHelper#callRefreshSystem
     * (storage) method to update the SMI-S database. The routine will check if any of
     * the passed in BlockObjects referenced by the URI list have their
     * emcRefreshRequired flag set. If so, the call will be made.
     * 
     * @param dbClient - DbClient for accessing the ViPR db
     * @param helper - SmisCommandHelper reference
     * @param storage - StorageSystem object that this refresh would be called against
     * @param blockObjectURIs - list of BlockObject URIs to check
     */
    public static void callEMCRefreshIfRequired(DbClient dbClient,
            SmisCommandHelper helper,
            StorageSystem storage,
            List<URI> blockObjectURIs) {
        try {
            if (blockObjectURIs != null) {
                List<URI> blockObjectsRequiringRefresh = new ArrayList<URI>();
                boolean refreshIsRequired = false;
                for (URI uri : blockObjectURIs) {
                    BlockObject object = BlockObject.fetch(dbClient, uri);
                    if (object.getRefreshRequired()) {
                        blockObjectsRequiringRefresh.add(uri);
                        refreshIsRequired = true;
                    }
                }

                if (refreshIsRequired) {
                    SimpleFunction toUpdateRefreshRequired =
                            new RefreshRequiredUpdateFunction(storage.getId(),
                                    blockObjectsRequiringRefresh, dbClient);
                    _log.info(String.format("Following objects require EMCRefresh, " +
                            "will attempt call:\n%s",
                            Joiner.on(',').join(blockObjectsRequiringRefresh)));
                    helper.callRefreshSystem(storage, toUpdateRefreshRequired);
                } else {
                    _log.info("No EMCRefresh is required");
                }
            }
        } catch (Exception e) {
            _log.error("Exception callEMCRefreshIfRequired", e);
        }
    }

    /**
     * Refresh the given storagesystem.
     * 
     * @param helper
     * @param storage
     */
    public static void callEMCRefresh(SmisCommandHelper helper, StorageSystem storage) {
        try {
            _log.info("Refreshing storagesystem: {}", storage.getId());
            helper.callRefreshSystem(storage, null);
        } catch (Exception e) {
            _log.error("Exception callEMCRefresh", e);
        }
    }

    /**
     * Refresh the given storagesystem.
     * 
     * @param helper reference to SmisCommandHelper
     * @param storage reference to StorageSystem
     * @param force flag to run refresh or not if threshold is not met
     */
    public static void callEMCRefresh(SmisCommandHelper helper, StorageSystem storage, boolean force) {
        try {
            _log.info("Refreshing storagesystem: {}", storage.getId());
            helper.callRefreshSystem(storage, null, force);
        } catch (Exception e) {
            _log.error("Exception callEMCRefresh", e);
        }
    }

    /**
     * Gets the default ReplicationSettingData object from the system and updates
     * the ConsistentPointInTime property to true.
     * 
     * @param storage
     * @param thinProvisioning
     * @return CIMInstance - the instance of ReplicaSettingData
     * @throws WBEMException
     */
    public static CIMInstance getReplicationSettingForGroupSnapshots(StorageSystem storage, SmisCommandHelper helper,
            CIMObjectPathFactory cimPath,
            boolean thinProvisioning) throws WBEMException {
        ReplicationSettingBuilder builder = new ReplicationSettingBuilder(storage, helper, cimPath);

        builder.addConsistentPointInTime();

        // For 4.6 providers and VMAX3 arrays, we are creating target devices and target group before
        // calling 'CreateGroupReplica'. We need to create new target devices while
        // creating group replica only for 8.0 provider.
        if (storage.getUsingSmis80() && !storage.checkIfVmax3()) {
            builder.addCreateNewTarget();
        }

        if (thinProvisioning) { // this should only apply to VMAX2
            builder.addVPSnap();
        }

        return builder.build();
    }

    /**
     * Gets the default ReplicationSettingData object from the system and updates
     * the ConsistentPointInTime property to true in addition to clone-specific settings.
     * 
     * @param storage
     * @return CIMInstance - the instance of ReplicaSettingData
     * @throws WBEMException
     */
    public static CIMInstance getReplicationSettingForGroupClones(StorageSystem storage, SmisCommandHelper helper,
            CIMObjectPathFactory cimPath,
            boolean createInactive) throws WBEMException {
        ReplicationSettingBuilder builder = new ReplicationSettingBuilder(storage, helper, cimPath);

        builder.addConsistentPointInTime();
        if (createInactive) {
            builder.addCopyBeforeActivate();
        } else {
            builder.addDifferentialClone();
        }
        return builder.build();
    }

    /**
     * Gets the default ReplicationSettingData object from the system and updates
     * the ConsistentPointInTime property to true in addition to clone-specific settings.
     * 
     * @param storage
     * @return CIMInstance - the instance of ReplicaSettingData
     * @throws WBEMException
     */
    public static CIMInstance getReplicationSettingForSMIS81TFGroupClones(StorageSystem storage, SmisCommandHelper helper,
            CIMObjectPathFactory cimPath,
            boolean createInactive) throws WBEMException {
        ReplicationSettingBuilder builder = new ReplicationSettingBuilder(storage, helper, cimPath);

        builder.addConsistentPointInTime();
        if (createInactive) {
            builder.addCopyBeforeActivate();
        } else {
            builder.addSMIS81TFDifferentialClone();
        }
        return builder.build();
    }

    /**
     * Gets the default ReplicationSettingData object from the system and updates
     * the ConsistentPointInTime property to true.
     * 
     * @param storage
     * @param helper
     * @param cimPath
     * @return CIMInstance - the instance of ReplicaSettingData
     * @throws WBEMException
     */
    public static CIMInstance getReplicationSettingForGroupMirrors(StorageSystem storage, SmisCommandHelper helper,
            CIMObjectPathFactory cimPath) throws WBEMException {
        ReplicationSettingBuilder builder = new ReplicationSettingBuilder(storage, helper, cimPath, MIRROR_REPLICATION_TYPE);
        builder.addConsistentPointInTime();
        return builder.build();
    }

    /**
     * Enables VPSnaps by modifying the default ReplicationSettingData instance.
     * 
     * @param storage The StorageSystem.
     * @return A modified ReplicationSettingData instance.
     * @throws WBEMException
     */
    public static CIMInstance getVPSnapReplicationSetting(StorageSystem storage, SmisCommandHelper helper,
            CIMObjectPathFactory cimPath) throws WBEMException {
        ReplicationSettingBuilder builder = new ReplicationSettingBuilder(storage, helper, cimPath);
        return builder.addVPSnap().addCreateNewTarget().build();
    }

    /**
     * Checks that the replication group is accessible from this storage system, using its currently active
     * storage provider.
     * 
     * @param storage StorageSystem
     * @param replica BlockObject
     * @throws com.emc.storageos.exceptions.DeviceControllerException When the replication group isn't found.
     */
    public static void checkReplicationGroupAccessibleOrFail(StorageSystem storage, BlockObject replica,
            DbClient dbClient, SmisCommandHelper helper, CIMObjectPathFactory cimPath) throws Exception {
        BlockConsistencyGroup blockConsistencyGroup = dbClient.queryObject(
                BlockConsistencyGroup.class, replica.getConsistencyGroup());
        String deviceName = ConsistencyUtils.getSourceConsistencyGroupName(replica, dbClient);
        String label = blockConsistencyGroup.getLabel();
        CIMObjectPath path = cimPath.getReplicationGroupPath(storage, deviceName);
        CIMInstance instance = helper.checkExists(storage, path, false, false);

        if (instance == null) {
            String msg = String.format("ReplicationGroup %s was not found on provider %s.  " +
                    "Check SMI-S providers for connection issues or failover.",
                    deviceName, storage.getActiveProviderURI());
            _log.warn(msg);
            throw DeviceControllerException.exceptions.consistencyGroupNotFoundForProvider(deviceName, label,
                    storage.getSmisProviderIP());
        }
    }

    public static CIMObjectPath getCloneGroupSynchronizedPath(StorageSystem storage, URI cloneUri,
            DbClient dbClient, SmisCommandHelper helper, CIMObjectPathFactory cimPath) {
        Volume clone = dbClient.queryObject(Volume.class, cloneUri);
        Volume sourceVol = dbClient.queryObject(Volume.class, clone.getAssociatedSourceVolume());
        String consistencyGroupName = ConsistencyUtils.getSourceConsistencyGroupName(sourceVol, dbClient);
        String replicationGroupName = clone.getReplicationGroupInstance();
        return cimPath.getGroupSynchronizedPath(storage, consistencyGroupName, replicationGroupName);
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
     * Method will invoke the SMI-S operation to create the target volumes
     * 
     * @param storageSystem - StorageSystem where the pool and snapshot exist
     * @param sourceGroupName - Name of source group
     * @param label - Name to be applied to each snapshot volume
     * @param createInactive - whether the snapshot needs to to be created with sync_active=true/false
     * @param count - Number of target Volumes to create
     * @param storagePoolUri - Storage Pool to use for creation of target volumes.
     * @param capacity - Size of the Volumes to create
     * @param isThinlyProvisioned
     * @param taskCompleter - Completer object used for task status update
     * @param dbClient
     * @param helper - smisCommandHelper
     * @param cimPath - CIMObjectPathFactory
     * 
     * @throws DeviceControllerException
     * 
     * @returns - List of native Ids
     */
    public static List<String> createTargetDevices(StorageSystem storageSystem, String sourceGroupName,
            String label, Boolean createInactive, int count,
            URI storagePoolUri, long capacity, boolean isThinlyProvisioned,
            Volume sourceVolume, TaskCompleter taskCompleter,
            DbClient dbClient, SmisCommandHelper helper, CIMObjectPathFactory cimPath)
            throws DeviceControllerException {

        _log.info(format("Creating target devices for: Storage System: {0}, Consistency Group: {1}, Pool: {2}, Count: {3}",
                storageSystem.getId(), sourceGroupName, storagePoolUri, count));

        try {
            StoragePool storagePool = dbClient.queryObject(StoragePool.class, storagePoolUri);
            CIMObjectPath configSvcPath = cimPath.getConfigSvcPath(storageSystem);
            CIMArgument[] inArgs = null;
            if (storageSystem.checkIfVmax3()) {
                CIMObjectPath volumeGroupPath = helper.getVolumeGroupPath(storageSystem, sourceVolume, storagePool);
                CIMObjectPath poolPath = helper.getPoolPath(storageSystem, storagePool);
                inArgs = helper.getCreateVolumesBasedOnVolumeGroupInputArguments(storageSystem, poolPath,
                        volumeGroupPath, label, count, capacity);
            } else {
                inArgs = helper.getCreateVolumesInputArguments(storageSystem, storagePool, label, capacity, count, isThinlyProvisioned,
                        null, true);
            }
            CIMArgument[] outArgs = new CIMArgument[5];

            SmisCreateVmaxCGTargetVolumesJob job = new SmisCreateVmaxCGTargetVolumesJob(null, storageSystem.getId(), sourceGroupName,
                    label, createInactive, taskCompleter);

            helper.invokeMethodSynchronously(storageSystem, configSvcPath,
                    helper.createVolumesMethodName(storageSystem), inArgs, outArgs, job);

            return job.getTargetDeviceIds();
        } catch (Exception e) {
            final String errMsg = format("An error occurred when creating target devices VMAX system {0}", storageSystem.getId());
            _log.error(errMsg, e);
            taskCompleter.error(dbClient,
                    SmisException.errors.methodFailed(helper.createVolumesMethodName(storageSystem), e.getMessage()));
            throw new SmisException(errMsg, e);
        }
    }

    /**
     * Creates a target group that will contain the devices in 'deviceIds'.
     * 
     * @param storage - StorageSystem where target device will be created
     * @param sourceGroupName - The name of the source volumes group
     * @param deviceIds - Device native IDs of the target VDEVs
     * @param taskCompleter - Completer object used for task status update
     * @return CIMObjectPath - null => Error. Otherwise, it represents the
     *         TargetDeviceGroup object created
     * 
     * @throws DeviceControllerException
     */
    public static CIMObjectPath createTargetDeviceGroup(StorageSystem storage,
            String sourceGroupName,
            List<String> deviceIds,
            TaskCompleter taskCompleter,
            DbClient dbClient,
            SmisCommandHelper helper,
            CIMObjectPathFactory cimPath,
            SYNC_TYPE syncType) throws DeviceControllerException {
        try {
            CIMObjectPath replicationSvc = cimPath.getControllerReplicationSvcPath(storage);
            CIMArgument[] outArgs = new CIMArgument[5];
            CIMObjectPath[] volumePaths = cimPath.getVolumePaths(storage, deviceIds.toArray(new String[deviceIds.size()]));
            CIMArgument[] inArgs = null;
            if (syncType == SYNC_TYPE.SNAPSHOT) {
                inArgs = helper.getCreateReplicationGroupCreateInputArguments(storage, null, volumePaths);
            } else {
                inArgs = helper.getCreateReplicationGroupWithMembersInputArguments(storage, null, volumePaths);
            }
            helper.invokeMethod(storage, replicationSvc, CREATE_GROUP, inArgs, outArgs);
            CIMObjectPath path = cimPath.getCimObjectPathFromOutputArgs(outArgs, CP_REPLICATION_GROUP);
            return path;
        } catch (Exception e) {
            taskCompleter.error(dbClient, SmisException.errors.methodFailed(CREATE_GROUP, e.getMessage()));
            throw new SmisException("Error when creating target device group", e);
        }
    }

    public static void rollbackCreateReplica(final StorageSystem storage,
            final CIMObjectPath targetGroupPath,
            final List<String> targetDeviceIds,
            final TaskCompleter taskCompleter,
            final DbClient dbClient,
            final SmisCommandHelper helper,
            final CIMObjectPathFactory cimPath) throws DeviceControllerException {

        _log.info(format("Rolling back snapshot creation on storage system {0}", storage.getId()));

        try {
            // Remove target group
            if (targetGroupPath != null) {
                deleteTargetDeviceGroup(storage, targetGroupPath, dbClient, helper, cimPath);
            }

            // Remove target devices
            if (targetDeviceIds != null && !targetDeviceIds.isEmpty()) {
                deleteTargetDevices(storage, targetDeviceIds.toArray(new String[targetDeviceIds.size()]), taskCompleter, dbClient, helper,
                        cimPath);
            }

        } catch (DeviceControllerException e) {
            final String errMsg = format("Unable to rollback snapshot creation on storage system {0}", storage.getId());
            _log.error(errMsg, e);
            throw new SmisException(errMsg, e);
        }
    }

    /**
     * Method will invoke the SMI-S operation to return the Volumes represented by the native ids to the storage pool
     * 
     * @param storageSystem - StorageSystem where the pool and volume exist
     * @param deviceIds - List of native Ids representing the elements to be returned to the pool
     * @param taskCompleter - Completer object used for task status update
     * 
     * @throws DeviceControllerException
     */
    public static void deleteTargetDevices(final StorageSystem storageSystem, final String[] deviceIds, final TaskCompleter taskCompleter,
            final DbClient dbClient, final SmisCommandHelper helper, final CIMObjectPathFactory cimPath) {

        _log.info(format("Removing target devices {0} from storage system {1}", deviceIds, storageSystem.getId()));

        try {
            if (storageSystem.checkIfVmax3()) {
                for (String deviceId : deviceIds) {
                    helper.removeVolumeFromParkingSLOStorageGroup(storageSystem, deviceId, false);
                    _log.info("Done invoking remove volume {} from parking SLO storage group", deviceId);
                }
            }

            CIMArgument[] outArgs = new CIMArgument[5];
            CIMArgument[] inArgs = null;
            String method = null;
            CIMObjectPath configSvcPath = cimPath.getConfigSvcPath(storageSystem);
            if (storageSystem.deviceIsType(Type.vmax)) {

                final CIMObjectPath[] theElements = cimPath.getVolumePaths(storageSystem, deviceIds);
                inArgs = helper.getReturnElementsToStoragePoolArguments(theElements,
                        SmisConstants.CONTINUE_ON_NONEXISTENT_ELEMENT);
                method = RETURN_ELEMENTS_TO_STORAGE_POOL;
            } else {
                inArgs = helper.getDeleteVolumesInputArguments(storageSystem, deviceIds);
                method = EMC_RETURN_TO_STORAGE_POOL;
            }
            final SmisDeleteVmaxCGTargetVolumesJob job = new SmisDeleteVmaxCGTargetVolumesJob(
                    null, storageSystem.getId(), deviceIds, taskCompleter);

            helper.invokeMethodSynchronously(storageSystem, configSvcPath, method, inArgs, outArgs, job);

        } catch (Exception e) {
            _log.error(
                    format("An error occurred when removing target devices {0} from storage system {1}", deviceIds, storageSystem.getId()),
                    e);
        }
    }

    public static CIMObjectPath getMirrorGroupSynchronizedPath(StorageSystem storage, URI mirrorUri,
            DbClient dbClient, SmisCommandHelper helper, CIMObjectPathFactory cimPath) {
        BlockMirror mirror = dbClient.queryObject(BlockMirror.class, mirrorUri);
        Volume sourceVol = dbClient.queryObject(Volume.class, mirror.getSource());
        String consistencyGroupName = ConsistencyUtils.getSourceConsistencyGroupName(sourceVol, dbClient);
        String replicationGroupName = mirror.getReplicationGroupInstance();
        return cimPath.getGroupSynchronizedPath(storage, consistencyGroupName, replicationGroupName);
    }

    /**
     * Deletes a replication group
     * 
     * @param storage StorageSystem
     * @param groupName replication group to be deleted
     * @param dbClient
     * @param helper
     * @param cimPath
     * 
     * @throws DeviceControllerException
     */
    public static void deleteReplicationGroup(final StorageSystem storage, final String groupName,
            final DbClient dbClient, final SmisCommandHelper helper, final CIMObjectPathFactory cimPath) {
        try {
            CIMObjectPath cgPath = cimPath.getReplicationGroupPath(storage, groupName);
            CIMObjectPath replicationSvc = cimPath.getControllerReplicationSvcPath(storage);
            CIMInstance cgPathInstance = helper.checkExists(storage, cgPath, false, false);
            if (cgPathInstance != null) {
                // Invoke the deletion of the consistency group
                CIMArgument[] inArgs = helper.getDeleteReplicationGroupInputArguments(storage, groupName);
                helper.invokeMethod(storage, replicationSvc, SmisConstants.DELETE_GROUP, inArgs, new CIMArgument[5]);
            }
        } catch (Exception e) {
            _log.error("Failed to delete replication group: ", e);
        }
    }

    /**
     * Utility function to remove a full copy that is being detached from its source
     * from the list full copies for the source volume.
     * 
     * @param fullCopy A reference to a full copy being detached from its source.
     * @param dbClient A reference to a database client.
     */
    public static void removeDetachedFullCopyFromSourceFullCopiesList(Volume fullCopy, DbClient dbClient) {
        URI sourceURI = fullCopy.getAssociatedSourceVolume();
        if ((!NullColumnValueGetter.isNullURI(sourceURI)) &&
                (URIUtil.isType(sourceURI, Volume.class))) {
            Volume sourceVolume = dbClient.queryObject(Volume.class, sourceURI);
            StringSet fullCopies = sourceVolume.getFullCopies();
            String fullCopyId = fullCopy.getId().toString();
            if ((fullCopies != null) && (fullCopies.contains(fullCopyId))) {
                fullCopies.remove(fullCopyId);
                dbClient.persistObject(sourceVolume);
            }
        }
    }
}

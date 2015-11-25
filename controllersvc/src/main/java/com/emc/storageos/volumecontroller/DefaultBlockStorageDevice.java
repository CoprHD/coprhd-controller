/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPolicy;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CleanupMetaVolumeMembersCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFMirrorCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeExpandCompleter;
import com.emc.storageos.volumecontroller.impl.smis.MetaVolumeRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/*
 * Default implementation of BlockStorageDevice, so that subclass can just overwrite necessary methods.
 */
public abstract class DefaultBlockStorageDevice implements BlockStorageDevice, RemoteMirroring {

    @Override
    public void doCreateVolumes(StorageSystem storage, StoragePool storagePool,
            String opId, List<Volume> volumes,
            VirtualPoolCapabilityValuesWrapper capabilities,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateMetaVolume(StorageSystem storage,
            StoragePool storagePool, Volume volume,
            VirtualPoolCapabilityValuesWrapper capabilities,
            MetaVolumeRecommendation recommendation,
            VolumeCreateCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doExpandVolume(StorageSystem storage, StoragePool pool,
            Volume volume, Long size, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doDeleteVolumes(StorageSystem storageSystem, String opId,
            List<Volume> volumes, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doExportGroupCreate(StorageSystem storage,
            ExportMask exportMask, Map<URI, Integer> volumeMap,
            List<Initiator> initiators, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doExportGroupDelete(StorageSystem storage,
            ExportMask exportMask, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doExportAddVolume(StorageSystem storage, ExportMask exportMask,
            URI volume, Integer lun, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doExportAddVolumes(StorageSystem storage,
            ExportMask exportMask, Map<URI, Integer> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doExportRemoveVolume(StorageSystem storage,
            ExportMask exportMask, URI volume, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doExportRemoveVolumes(StorageSystem storage,
            ExportMask exportMask, List<URI> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doExportAddInitiator(StorageSystem storage,
            ExportMask exportMask, Initiator initiator, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doExportAddInitiators(StorageSystem storage,
            ExportMask exportMask, List<Initiator> initiators,
            List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doExportRemoveInitiator(StorageSystem storage,
            ExportMask exportMask, Initiator initiator, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doExportRemoveInitiators(StorageSystem storage,
            ExportMask exportMask, List<Initiator> initiators,
            List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateSingleSnapshot(StorageSystem storage, List<URI> snapshotList,
                                         Boolean createInactive, Boolean readOnly, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateSnapshot(StorageSystem storage, List<URI> snapshotList,
            Boolean createInactive, Boolean readOnly, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doActivateSnapshot(StorageSystem storage,
            List<URI> snapshotList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doDeleteSnapshot(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doDeleteSelectedSnapshot(StorageSystem storage, URI snapshot,
                                         TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doRestoreFromSnapshot(StorageSystem storage, URI volume,
            URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateMirror(StorageSystem storage, URI mirror,
            Boolean createInactive, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doFractureMirror(StorageSystem storage, URI mirror,
            Boolean sync, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doDetachMirror(StorageSystem storage, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doResumeNativeContinuousCopy(StorageSystem storage, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doEstablishVolumeNativeContinuousCopyGroupRelation(
            StorageSystem storage, URI sourceVolume, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doEstablishVolumeSnapshotGroupRelation(
            StorageSystem storage, URI sourceVolume, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }
    
    @Override
    public void doChangeCopyMode(StorageSystem system, Volume target,
            TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doDeleteMirror(StorageSystem storage, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateClone(StorageSystem storageSystem, URI sourceVolume,
            URI cloneVolume, Boolean createInactive, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doDetachClone(StorageSystem storage, URI cloneVolume,
            TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doRestoreFromClone(StorageSystem storage, URI cloneVolume,
            TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateConsistencyGroup(StorageSystem storage,
            URI consistencyGroup, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doDeleteConsistencyGroup(StorageSystem storage,
            URI consistencyGroup, Boolean markInactive, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doConnect(StorageSystem storage) {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doDisconnect(StorageSystem storage) {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public String doAddStorageSystem(StorageSystem storage)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doRemoveStorageSystem(StorageSystem storage)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doCopySnapshotsToTarget(StorageSystem storage,
            List<URI> snapshotList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames, boolean mustHaveAllPorts) {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doActivateFullCopy(StorageSystem storageSystem, URI fullCopy,
            TaskCompleter completer) {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doCleanupMetaMembers(StorageSystem storageSystem,
            Volume volume, CleanupMetaVolumeMembersCompleter cleanupCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public Integer checkSyncProgress(URI storage, URI source, URI target) {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doWaitForSynchronized(Class<? extends BlockObject> clazz,
            StorageSystem storageObj, URI target, TaskCompleter completer) {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doWaitForGroupSynchronized(StorageSystem storageObj, List<URI> target, TaskCompleter completer) {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doAddToConsistencyGroup(StorageSystem storage,
            URI consistencyGroupId, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doRemoveFromConsistencyGroup(StorageSystem storage,
            URI consistencyGroupId, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doAddToReplicationGroup(StorageSystem storage,
            URI consistencyGroupId, String replicationGroupName, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doRemoveFromReplicationGroup(StorageSystem storage,
            URI consistencyGroupId, String replicationGroupName, List<URI> blockObjects,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doExpandAsMetaVolume(StorageSystem storageSystem,
            StoragePool storagePool, Volume volume, long size,
            MetaVolumeRecommendation recommendation,
            VolumeExpandCompleter volumeCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public boolean validateStorageProviderConnection(String ipAddress,
            Integer portNumber) {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateMetaVolumes(StorageSystem storage,
            StoragePool storagePool, List<Volume> volumes,
            VirtualPoolCapabilityValuesWrapper capabilities,
            MetaVolumeRecommendation recommendation, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void updatePolicyAndLimits(StorageSystem storage,
            ExportMask exportMask, List<URI> volumeURIs, VirtualPool newVpool,
            boolean rollback, TaskCompleter taskCompleter) throws Exception {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public ExportMaskPolicy getExportMaskPolicy(StorageSystem storage, ExportMask mask) {
        return new ExportMaskPolicy();
    }

    @Override
    public void doModifyVolumes(StorageSystem storage, StoragePool storagePool,
            String opId, List<Volume> volumes, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doTerminateAnyRestoreSessions(StorageSystem storageDevice,
            URI source, BlockObject snapshot, TaskCompleter completer)
            throws Exception {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doFractureClone(StorageSystem storageDevice, URI source, URI clone,
            TaskCompleter completer) {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doResyncClone(StorageSystem storageDevice, URI clone,
            TaskCompleter completer) {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateGroupClone(StorageSystem storageDevice, List<URI> clones,
            Boolean createInactive, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDetachGroupClone(StorageSystem storage, List<URI> cloneVolume,
            TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doEstablishVolumeFullCopyGroupRelation(
            StorageSystem storage, URI sourceVolume, URI fullCopy,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doRestoreFromGroupClone(StorageSystem storageSystem,
            List<URI> cloneVolume, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doActivateGroupFullCopy(StorageSystem storageSystem,
            List<URI> fullCopy, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doResyncGroupClone(StorageSystem storageDevice, List<URI> clone,
            TaskCompleter completer) throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doFractureGroupClone(StorageSystem storageDevice,
            List<URI> clone, TaskCompleter completer) throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void doAddVolumePairsToCg(StorageSystem system, List<URI> sources, URI remoteDirectorGroup, boolean forceAdd,
            TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateLink(StorageSystem system, URI source, URI target, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateListReplicas(StorageSystem system, List<URI> sources, List<URI> targets, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDetachLink(StorageSystem system, URI source, URI target, boolean onGroup, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doRemoveDeviceGroups(StorageSystem system, URI source, URI target, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doRollbackLinks(StorageSystem system, List<URI> sources,
            List<URI> targets, boolean isGroupRollback, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doSplitLink(StorageSystem system, Volume target, boolean rollback, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doSuspendLink(StorageSystem system, Volume target, boolean consExempt, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doResumeLink(StorageSystem system, Volume target, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doFailoverLink(StorageSystem system, Volume target, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doFailoverCancelLink(StorageSystem system, Volume target, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doResyncLink(StorageSystem system, URI source, URI target, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doRemoveVolumePair(StorageSystem system, URI source, URI target, boolean rollback,
            TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doStartLink(StorageSystem system, Volume target, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doStopLink(StorageSystem system, Volume target, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateCgPairs(StorageSystem system, List<URI> sources, List<URI> targets,
            SRDFMirrorCreateCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public Set<String> findVolumesPartOfRemoteGroup(StorageSystem system, RemoteDirectorGroup rdfGroup) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doSwapVolumePair(StorageSystem system, Volume target, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doSyncLink(StorageSystem system, Volume target, TaskCompleter completer) throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doUpdateSourceAndTargetPairings(List<URI> sourceURIs, List<URI> targetURIs) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void refreshStorageSystem(URI systemURI, List<URI> volumeURIs) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doResyncSnapshot(StorageSystem storage, URI volume,
            URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateGroupMirrors(StorageSystem storage,
            List<URI> mirrorList, Boolean createInactive,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doFractureGroupMirrors(StorageSystem storage,
            List<URI> mirrorList, Boolean sync, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDetachGroupMirrors(StorageSystem storage,
            List<URI> mirrorList, Boolean deleteGroup, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doResumeGroupNativeContinuousCopies(StorageSystem storage,
            List<URI> mirrorList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDeleteGroupMirrors(StorageSystem storage,
            List<URI> mirrorList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doRemoveMirrorFromDeviceMaskingGroup(StorageSystem system,
            List<URI> mirrors, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doCreateListReplica(StorageSystem storage, List<URI> replicaList, Boolean createInactive, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void doDetachListReplica(StorageSystem storage, List<URI> replicaList, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public Map<URI, Integer> getExportMaskHLUs(StorageSystem storage, ExportMask exportMask) {
        return Collections.EMPTY_MAP;
    }
    
    @Override
    public void doUntagVolumes(StorageSystem storageSystem, String opId, List<Volume> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        // If this operation is unsupported by default it's Ok.
        return;
    }
}

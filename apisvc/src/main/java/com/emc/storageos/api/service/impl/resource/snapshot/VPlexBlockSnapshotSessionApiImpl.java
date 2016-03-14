/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.vplexcontroller.VPlexController;

/**
 * Block snapshot session implementation for volumes on VPLEX systems.
 */
public class VPlexBlockSnapshotSessionApiImpl extends DefaultBlockSnapshotSessionApiImpl {

    /**
     * Private default constructor should not be called outside class.
     */
    @SuppressWarnings("unused")
    private VPlexBlockSnapshotSessionApiImpl() {
        super();
    }

    /**
     * Constructor.
     * 
     * @param dbClient A reference to a data base client.
     * @param coordinator A reference to the coordinator client.
     * @param permissionsHelper A reference to a permission helper.
     * @param securityContext A reference to the security context.
     * @param blockSnapshotSessionMgr A reference to the snapshot session manager.
     */
    public VPlexBlockSnapshotSessionApiImpl(DbClient dbClient, CoordinatorClient coordinator, PermissionsHelper permissionsHelper,
            SecurityContext securityContext, BlockSnapshotSessionManager blockSnapshotSessionMgr) {
        super(dbClient, coordinator, permissionsHelper, securityContext, blockSnapshotSessionMgr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj, List<BlockObject> sourceObjList, Project project,
            String name, int newTargetsCount, String newTargetsName, String newTargetCopyMode, boolean skipInternalCheck,
            BlockFullCopyManager fcManager) {
        // We can only create a snapshot session for a VPLEX volume, where the
        // source side backend volume supports the creation of a snapshot session.
        for (BlockObject sourceObj : sourceObjList) {
            URI sourceURI = sourceObj.getId();
            if (URIUtil.isType(sourceURI, Volume.class)) {
                // Get the platform specific implementation for the source side
                // backend storage system and call the validation routine.
                Volume vplexVolume = (Volume) sourceObj;
                BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
                BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
                snapSessionImpl.validateSnapshotSessionCreateRequest(srcSideBackendVolume, Arrays.asList(srcSideBackendVolume),
                        project, name, newTargetsCount, newTargetsName, newTargetCopyMode, true, fcManager);

                // Check for pending tasks on the VPLEX source volume.
                checkForPendingTasks(vplexVolume, vplexVolume.getTenant().getURI());
            } else {
                // We don't currently support snaps of BlockSnapshot instances
                // so should never be called.
                throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSnapshotSession(BlockObject sourceObj, URI snapSessionURI,
            List<List<URI>> snapSessionSnapshotURIs, String copyMode, String taskId) {
        if (URIUtil.isType(sourceObj.getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the create method.
            Volume vplexVolume = (Volume) sourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.createSnapshotSession(srcSideBackendVolume, snapSessionURI, snapSessionSnapshotURIs, copyMode, taskId);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateLinkNewTargetsRequest(BlockObject snapSessionSourceObj, Project project, int newTargetsCount,
            String newTargetsName, String newTargetCopyMode) {
        URI sourceURI = snapSessionSourceObj.getId();
        if (URIUtil.isType(sourceURI, Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the validation routine.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.validateLinkNewTargetsRequest(srcSideBackendVolume, project, newTargetsCount, newTargetsName,
                    newTargetCopyMode);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void linkNewTargetVolumesToSnapshotSession(BlockObject snapSessionSourceObj, BlockSnapshotSession snapSession,
            List<List<URI>> snapshotURIs, String copyMode, String taskId) {
        if (URIUtil.isType(snapSessionSourceObj.getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the link method.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.linkNewTargetVolumesToSnapshotSession(srcSideBackendVolume, snapSession, snapshotURIs, copyMode, taskId);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateRelinkSnapshotSessionTargets(BlockObject snapSessionSourceObj, BlockSnapshotSession tgtSnapSession,
            Project project, List<URI> snapshotURIs, UriInfo uriInfo) {
        URI sourceURI = snapSessionSourceObj.getId();
        if (URIUtil.isType(sourceURI, Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the validation routine.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.validateRelinkSnapshotSessionTargets(srcSideBackendVolume, tgtSnapSession, project, snapshotURIs, uriInfo);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void relinkTargetVolumesToSnapshotSession(BlockObject snapSessionSourceObj, BlockSnapshotSession TgtSnapSession,
            List<URI> snapshotURIs, String taskId) {
        if (URIUtil.isType(snapSessionSourceObj.getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the relink method.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.relinkTargetVolumesToSnapshotSession(srcSideBackendVolume, TgtSnapSession, snapshotURIs, taskId);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateUnlinkSnapshotSessionTargets(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, Project project,
            Map<URI, Boolean> targetMap, UriInfo uriInfo) {
        URI sourceURI = snapSessionSourceObj.getId();
        if (URIUtil.isType(sourceURI, Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the validation routine.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.validateUnlinkSnapshotSessionTargets(snapSession, srcSideBackendVolume, project, targetMap,
                    uriInfo);

            // For VPLEX, the linked target volume must be deleted when they are unlinked.
            // If we allow this, then you end up with a public Volume instance that is not
            // a VPLEX volume, but has a vpool that specifies VPLEX HA. This causes many
            // problems, because we end up using the VPlexBlockServiceApiImpl to perform
            // block operations on a non-VPLEX volume.
            for (Entry<URI, Boolean> targetEntry : targetMap.entrySet()) {
                URI snapshotURI = targetEntry.getKey();
                Boolean deleteTarget = targetEntry.getValue();
                if (Boolean.FALSE == deleteTarget) {
                    // For VPLEX, the linked target volume must be deleted when they are unlinked.
                    // If we allow this, then you end up with a public Volume instance that is not
                    // a VPLEX volume, but has a vpool that specifies VPLEX HA. This causes many
                    // problems, because we end up using the VPlexBlockServiceApiImpl to perform
                    // block operations on a non-VPLEX volume.
                    throw APIException.badRequests.mustDeleteTargetsOnUnlinkForVPlex();
                } else {
                    // Don't allow if there is a VPLEX volume built on the linked target volume.
                    // The VPLEX volume must be deleted first.
                    BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotURI);
                    String snapshotNativeGuid = snapshot.getNativeGuid();
                    List<Volume> volumesWithSameNativeGuid = CustomQueryUtility.getActiveVolumeByNativeGuid(_dbClient, snapshotNativeGuid);
                    if (!volumesWithSameNativeGuid.isEmpty()) {
                        // There should only be one and it should be a backend volume for
                        // a VPLEX volume.
                        List<Volume> vplexVolumes = CustomQueryUtility.queryActiveResourcesByConstraint(
                                _dbClient, Volume.class, AlternateIdConstraint.Factory.getVolumeByAssociatedVolumesConstraint(
                                        volumesWithSameNativeGuid.get(0).getId().toString()));
                        throw APIException.badRequests.cantDeleteSnapshotExposedByVolume(snapshot.getLabel().toString(),
                                vplexVolumes.get(0).getLabel());
                    }
                }
            }
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlinkTargetVolumesFromSnapshotSession(BlockObject snapSessionSourceObj, BlockSnapshotSession snapSession,
            Map<URI, Boolean> snapshotDeletionMap, String taskId) {
        if (URIUtil.isType(snapSessionSourceObj.getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the unlink target method.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.unlinkTargetVolumesFromSnapshotSession(srcSideBackendVolume, snapSession, snapshotDeletionMap, taskId);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateRestoreSnapshotSession(List<BlockObject> snapSessionSourceObjs, Project project) {
        if (URIUtil.isType(snapSessionSourceObjs.get(0).getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the validation routine.
            List<BlockObject> srcSideBackendVolumes = new ArrayList<>();
            for (BlockObject snapSessionSourceObj : snapSessionSourceObjs) {
                Volume vplexVolume = (Volume) snapSessionSourceObj;
                srcSideBackendVolumes.add(VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient));
            }
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(
                    srcSideBackendVolumes.get(0).getStorageController());
            snapSessionImpl.validateRestoreSnapshotSession(srcSideBackendVolumes, project);

            for (BlockObject snapSessionSourceObj : snapSessionSourceObjs) {
                Volume vplexVolume = (Volume) snapSessionSourceObj;

                // Check for pending tasks on the VPLEX source volume.
                checkForPendingTasks(vplexVolume, vplexVolume.getTenant().getURI());

                // Verify no active mirrors on the VPLEX volume.
                verifyActiveMirrors(vplexVolume);
            }
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restoreSnapshotSession(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, String taskId) {
        // Because the source is a VPLEX volume, when the native array snapshot is restored, the
        // data on the source side backend volume will be restored to the data on the backend array
        // snapshot. This means we have to perform operations on the VPLEX volume to ensure it
        // recognizes that the data has been changed.
        if (URIUtil.isType(snapSessionSourceObj.getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the validation routine.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            URI vplexURI = vplexVolume.getStorageController();
            VPlexController controller = getController(VPlexController.class,
                    DiscoveredDataObject.Type.vplex.toString());
            controller.restoreSnapshotSession(vplexURI, snapSession.getId(), taskId);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateDeleteSnapshotSession(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, Project project) {
        if (URIUtil.isType(snapSessionSourceObj.getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the validation routine.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.validateDeleteSnapshotSession(snapSession, srcSideBackendVolume, project);

            // Check for pending tasks on the VPLEX source volume.
            checkForPendingTasks(snapSession, vplexVolume.getTenant().getURI());
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteSnapshotSession(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, String taskId,
            String deleteType) {
        if (URIUtil.isType(snapSessionSourceObj.getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the delete method.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.deleteSnapshotSession(snapSession, srcSideBackendVolume, taskId, deleteType);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockSnapshotSession> getSnapshotSessionsForSource(BlockObject sourceObj) {
        List<BlockSnapshotSession> snapSessions;
        if (URIUtil.isType(sourceObj.getId(), Volume.class)) {
            Volume vplexVolume = (Volume) sourceObj;
            Volume srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            URI parentURI = srcSideBackendVolume.getId();
            snapSessions = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, BlockSnapshotSession.class,
                    ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(parentURI));
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should not be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }

        return snapSessions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlockSnapshotSession prepareSnapshotSessionFromSource(BlockObject sourceObj, String snapSessionLabel, String instanceLabel,
            String taskId, boolean inApplication) {
        // The snapshot is generally prepared with information from the
        // source side backend volume, which is the volume being snapped.
        // The passed source object will be a volume, else would not have
        // made it this far.
        Volume srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume((Volume) sourceObj, true, _dbClient);
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
        BlockSnapshotSession snapSession = snapSessionImpl.prepareSnapshotSessionFromSource(srcSideBackendVolume, snapSessionLabel,
                instanceLabel, taskId, inApplication);

        // However, the project is from the VPLEX volume.
        Project sourceProject = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(sourceObj, _dbClient);
        snapSession.setProject(new NamedURI(sourceProject.getId(), sourceObj.getLabel()));

        return snapSession;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlockSnapshot prepareSnapshotForSession(BlockObject sourceObj, String snapsetLabel, String instanceLabel) {
        // The snapshot is generally prepared with information from the
        // source side backend volume, which is the volume being snapped.
        // The passed source object will be a volume, else would not have
        // made it this far.
        Volume srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume((Volume) sourceObj, true, _dbClient);
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
        BlockSnapshot snapshot = snapSessionImpl.prepareSnapshotForSession(srcSideBackendVolume, snapsetLabel, instanceLabel);

        // However, the project is from the VPLEX volume.
        Project sourceProject = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(sourceObj, _dbClient);
        snapshot.setProject(new NamedURI(sourceProject.getId(), sourceObj.getLabel()));
        _dbClient.updateObject(snapshot);

        return snapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Map<URI, BlockSnapshot>> prepareSnapshotsForSession(List<BlockObject> sourceObjList, int sourceCount, int newTargetCount,
            String newTargetsName, boolean inApplication) {
        // The snapshots are generally prepared with information from the
        // source side backend volume, which is the volume being snapped.
        // The passed source object will be a volume, else would not have
        // made it this far.
        List<BlockObject> srcSideBackendVolumes = new ArrayList<>();
        for (BlockObject sourceObj : sourceObjList) {
            srcSideBackendVolumes.add(VPlexUtil.getVPLEXBackendVolume((Volume) sourceObj, true, _dbClient));
        }
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolumes.get(0).getStorageController());
        List<Map<URI, BlockSnapshot>> snapshotMap = snapSessionImpl.prepareSnapshotsForSession(srcSideBackendVolumes, sourceCount,
                newTargetCount, newTargetsName, inApplication);

        // However, the project is from the VPLEX volume.
        Project sourceProject = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(sourceObjList.get(0), _dbClient);
        for (Map<URI, BlockSnapshot> snapshots : snapshotMap) {
            for (BlockSnapshot snapshot : snapshots.values()) {
                snapshot.setProject(new NamedURI(sourceProject.getId(), sourceObjList.get(0).getLabel()));
                _dbClient.updateObject(snapshot);
            }
        }

        return snapshotMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyActiveMirrors(Volume sourceVolume) {
        // Check for VPLEX mirrors.
        List<URI> activeMirrorsForSource = BlockServiceUtils.getActiveMirrorsForVplexVolume(sourceVolume, _dbClient);
        if (!activeMirrorsForSource.isEmpty()) {
            throw APIException.badRequests.snapshotSessionSourceHasActiveMirrors(
                    sourceVolume.getLabel(), activeMirrorsForSource.size());
        }
    }
}

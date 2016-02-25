/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.BlockServiceApi;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.ResourceOnlyNameGenerator;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

/**
 * 
 */
public class DefaultBlockSnapshotSessionApiImpl implements BlockSnapshotSessionApi {

    // A reference to a database client.
    protected DbClient _dbClient;

    // A reference to the coordinator.
    protected CoordinatorClient _coordinator = null;

    // A reference to a permissions helper.
    private PermissionsHelper _permissionsHelper = null;

    // A reference to the security context
    private SecurityContext _securityContext;

    // A reference to the snapshot session manager
    protected BlockSnapshotSessionManager _blockSnapshotSessionMgr;

    @SuppressWarnings("unused")
    private static final Logger s_logger = LoggerFactory.getLogger(DefaultBlockSnapshotSessionApiImpl.class);

    /**
     * Protected default constructor.
     */
    protected DefaultBlockSnapshotSessionApiImpl() {
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
    public DefaultBlockSnapshotSessionApiImpl(DbClient dbClient, CoordinatorClient coordinator, PermissionsHelper permissionsHelper,
            SecurityContext securityContext, BlockSnapshotSessionManager blockSnapshotSessionMgr) {
        _dbClient = dbClient;
        _coordinator = coordinator;
        _permissionsHelper = permissionsHelper;
        _securityContext = securityContext;
        _blockSnapshotSessionMgr = blockSnapshotSessionMgr;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj, List<BlockObject> sourceObjList, Project project,
            String name, int newTargetsCount, String newTargetsName, String newTargetCopyMode, boolean skipInternalCheck,
            BlockFullCopyManager fcManager) {

        // Validate the project tenant.
        TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, project.getTenantOrg().getURI());
        ArgValidator.checkEntity(tenant, project.getTenantOrg().getURI(), false);

        // Verify the user is authorized.
        BlockServiceUtils.verifyUserIsAuthorizedForRequest(project,
                BlockServiceUtils.getUserFromContext(_securityContext), _permissionsHelper);

        // Verify a name was specified in the request.
        ArgValidator.checkFieldNotEmpty(name, "name");

        // Verify the source objects.
        List<Volume> sourceVolumeList = new ArrayList<Volume>();
        for (BlockObject sourceObj : sourceObjList) {
            URI sourceURI = sourceObj.getId();
            if (URIUtil.isType(sourceURI, Volume.class)) {
                Volume sourceVolume = (Volume) sourceObj;

                // Make sure that we don't have some pending
                // operation against the volume.
                checkForPendingTasks(sourceVolume, sourceVolume.getTenant().getURI());

                // Verify the operation is supported for ingested volumes.
                VolumeIngestionUtil.checkOperationSupportedOnIngestedVolume(sourceVolume,
                        ResourceOperationTypeEnum.CREATE_SNAPSHOT_SESSION, _dbClient);

                // Verify the source is not an internal object.
                if (!skipInternalCheck) {
                    BlockServiceUtils.validateNotAnInternalBlockObject(sourceObj, false);
                }

                // Verify that array snapshots are allowed.
                VirtualPool vpool = BlockSnapshotSessionUtils.querySnapshotSessionSourceVPool(sourceObj, _dbClient);
                int maxVpoolSnaps = vpool.getMaxNativeSnapshots().intValue();
                if (maxVpoolSnaps == 0) {
                    throw APIException.badRequests.maxNativeSnapshotsIsZero(vpool.getLabel());
                }

                // Verify the number of array snapshots does not exceed
                // the limit specified by the virtual pool.
                int numNativeArraySnapshots = getNumNativeSnapshots(sourceVolume);
                if (numNativeArraySnapshots >= maxVpoolSnaps) {
                    throw APIException.badRequests.maximumNumberVpoolSnapshotsReached(sourceURI.toString());
                }

                // Verify the number of array snapshots does not exceed
                // the limit specified by the platform.
                int maxSnapsForSource = getMaxSnapshotsForSource();
                if (numNativeArraySnapshots >= maxSnapsForSource) {
                    throw APIException.badRequests.maximumNumberSnapshotsForSourceReached(sourceURI.toString());
                }

                // Check for duplicate name.
                checkForDuplicateSnapshotName(name, sourceVolume);

                // Verify the new target count. There can be restrictions on the
                // number of targets that can be linked to the snapshot sessions
                // for a given source.
                verifyNewTargetCount(sourceObj, newTargetsCount, true);
            } else {
                // TBD Future - What if source is a BlockSnapshot i.e., cascaded snapshot?
                // What about when the source is a BlockSnapshot. It has no vpool
                // and no max snaps value. It could be determined from and be the
                // same as the source device. Or, these cascaded snaps could be
                // cumulative and count against the max for the source.
                throw APIException.badRequests.createSnapSessionNotSupportForSnapshotSource();
            }
        }

        // Some systems, such as VMAX3, don't support array snapshots when there
        // are active full copies sessions on the source.
        URI requestSourceObjURI = requestedSourceObj.getId();
        if (URIUtil.isType(requestSourceObjURI, Volume.class)) {
            fcManager.validateSnapshotCreateRequest((Volume) requestedSourceObj, sourceVolumeList);
        }
    }

    /**
     * Checks for pending tasks on the passed data object.
     * 
     * @param object A reference to a data object.
     * @param tenantURI The URI of the tenant.
     */
    protected <T extends DataObject> void checkForPendingTasks(T object, URI tenantURI) {
        BlockServiceUtils.checkForPendingTasks(tenantURI, Arrays.asList(object), _dbClient);
    }

    /**
     * Counts and returns the number of arrays snapshots for
     * the passed volume. Should be overridden when the number
     * of native snapshots are determined in a different manner
     * for the platform.
     * 
     * @param volume A reference to a snapshot source volume.
     * 
     * @return The number of array snapshots on a volume.
     */
    protected int getNumNativeSnapshots(Volume volume) {
        return BlockServiceUtils.getNumNativeSnapshots(volume.getId(), _dbClient);
    }

    /**
     * Get the maximum number of snapshots allowed for a source
     * on the storage system. Should be overridden for each
     * platform as needed.
     * 
     * @return The maximum number of snapshots allowed for a source.
     */
    protected int getMaxSnapshotsForSource() {
        return Integer.MAX_VALUE;
    }

    /**
     * Check if a array snapshot with the same name exists for the passed volume.
     * Note that we need to compare the passed name to the session label for
     * the volumes snapshot sessions because the actual session names can have
     * an appended suffix when the volume is in a CG with multiple volumes.
     * Also, we need to run the name through the generator, which is done
     * prior to setting the session label for a snapshot session. Should be overridden
     * when the manner in which duplicate names are checked is determined in a
     * different manner for the platform.
     * 
     * @param requestedName The name to verify.
     * @param volume A reference to a snapshot source volume.
     */
    protected void checkForDuplicateSnapshotName(String requestedName, Volume volume) {
        BlockServiceUtils.checkForDuplicateArraySnapshotName(requestedName, volume.getId(), _dbClient);
    }

    /**
     * Verifies that the number of targets to be linked to a snapshot session
     * is valid for the specified source. Should be overridden when there are
     * different or additional platform restrictions.
     * 
     * @param sourceObj A reference to the snapshot session source
     * @param newTargetsCount The number of new targets to be linked to a session.
     * @param zeroIsValid true if zero is a valid count, false otherwise.
     */
    protected void verifyNewTargetCount(BlockObject sourceObj, int newTargetsCount, boolean zeroIsValid) {
        // If zero is no valid and the value is zero, throw an error.
        if ((!zeroIsValid) && (newTargetsCount == 0)) {
            throw APIException.badRequests.invalidZeroLinkedTargetsRequested();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlockSnapshotSession prepareSnapshotSession(List<BlockObject> sourceObjList, String snapSessionLabel, int newTargetCount,
            String newTargetsName, List<Map<URI, BlockSnapshot>> snapSessionSnapshots, String taskId) {
        // Create a single snap session based on a sample volume in the CG
        BlockObject source = sourceObjList.get(0);
        BlockSnapshotSession snapSession = prepareSnapshotSessionFromSource(source, snapSessionLabel, snapSessionLabel, taskId);

        /*
         * If linked targets are requested...
         * 
         * Non-CG case for source ["src"] with 2 targets named "linked":
         * [
         * ["linked-1"],
         * ["linked-2"]
         * ]
         * 
         * CG case for sources ["src-1", "src-2"] with 2 targets named "linked":
         * [
         * ["linked-1-1", "linked-1-2"],
         * ["linked-2-1", "linked-2-2"]
         * ]
         * 
         * i.e. treat non-CG single volumes as single member groups, then for however
         * many target requests, copy each group.
         */
        if (newTargetCount > 0) {
            // Create <newTargetCount> lists of targets
            // Snapset labels use format <newTargetsName>-<currentTargetCount>
            // Labels will look like <snapsetLabel>-<count>
            for (int i = 0; i < newTargetCount; i++) {
                int count = 0;
                Map<URI, BlockSnapshot> targetMap = new HashMap<>();
                for (BlockObject sourceObj : sourceObjList) {
                    // Generate label here
                    String snapsetLabel = String.format("%s-%s", newTargetsName, i + 1);
                    String label = snapsetLabel;
                    String rgName = sourceObj.getReplicationGroupInstance();
                    if (sourceObj instanceof Volume && ((Volume) sourceObj).isVPlexVolume(_dbClient)) {
                        // get RG name from back end volume
                        Volume srcBEVolume = VPlexUtil.getVPLEXBackendVolume((Volume) sourceObj, true, _dbClient);
                        rgName = srcBEVolume.getReplicationGroupInstance();
                    }
                    if (NullColumnValueGetter.isNotNullValue(rgName)) {
                        // There can be multiple RGs in a CG, in such cases generate unique name
                        if (sourceObjList.size() > 1) {
                            label = String.format("%s-%s-%s", snapsetLabel, rgName, ++count);
                        } else {
                            label = String.format("%s-%s", snapsetLabel, rgName);
                        }
                    } else if (sourceObjList.size() > 1) {
                        label = String.format("%s-%s", snapsetLabel, ++count);
                    }

                    BlockSnapshot blockSnapshot = prepareSnapshotForSession(sourceObj, snapsetLabel, label);
                    targetMap.put(blockSnapshot.getId(), blockSnapshot);
                }
                snapSessionSnapshots.add(targetMap);
            }
        }

        // Create and return the prepared snapshot session.
        _dbClient.createObject(snapSession);
        return snapSession;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlockSnapshotSession prepareSnapshotSessionFromSource(BlockObject sourceObj, String snapSessionLabel, String instanceLabel,
            String taskId) {
        BlockSnapshotSession snapSession = new BlockSnapshotSession();
        Project sourceProject = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(sourceObj, _dbClient);

        snapSession.setId(URIUtil.createId(BlockSnapshotSession.class));

        snapSession.setProject(new NamedURI(sourceProject.getId(), sourceObj.getLabel()));
        snapSession.setStorageController(sourceObj.getStorageController());

        if (sourceObj.hasConsistencyGroup()) {
            snapSession.setConsistencyGroup(sourceObj.getConsistencyGroup());
            snapSession.setSessionSetName(snapSessionLabel);
            String rgName = sourceObj.getReplicationGroupInstance();
            if (NullColumnValueGetter.isNotNullValue(rgName)) {
                snapSession.setReplicationGroupInstance(rgName);
                // append RG name to user given label to uniquely identify sessions
                // when there are multiple RGs in a CG
                instanceLabel = String.format("%s-%s", instanceLabel, rgName);
            }
        } else {
            snapSession.setParent(new NamedURI(sourceObj.getId(), sourceObj.getLabel()));
        }

        snapSession.setLabel(instanceLabel);
        snapSession.setSessionLabel(ResourceOnlyNameGenerator.removeSpecialCharsForName(snapSessionLabel,
                SmisConstants.MAX_SNAPSHOT_NAME_LENGTH));

        return snapSession;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlockSnapshot prepareSnapshotForSession(BlockObject sourceObj, String snapsetLabel, String label) {
        BlockSnapshot snapshot = new BlockSnapshot();

        snapshot.setId(URIUtil.createId(BlockSnapshot.class));
        URI cgUri = sourceObj.getConsistencyGroup();
        if (cgUri != null) {
            snapshot.setConsistencyGroup(cgUri);
        }
        snapshot.setSourceNativeId(sourceObj.getNativeId());
        snapshot.setParent(new NamedURI(sourceObj.getId(), sourceObj.getLabel()));
        snapshot.setLabel(label);
        snapshot.setStorageController(sourceObj.getStorageController());
        snapshot.setVirtualArray(sourceObj.getVirtualArray());
        snapshot.setProtocol(new StringSet());
        snapshot.getProtocol().addAll(sourceObj.getProtocol());
        Project sourceProject = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(sourceObj, _dbClient);
        snapshot.setProject(new NamedURI(sourceProject.getId(), sourceObj.getLabel()));
        snapshot.setSnapsetLabel(ResourceOnlyNameGenerator.removeSpecialCharsForName(
                snapsetLabel, SmisConstants.MAX_SNAPSHOT_NAME_LENGTH));
        snapshot.setTechnologyType(BlockSnapshot.TechnologyType.NATIVE.name());

        _dbClient.createObject(snapshot);
        return snapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Map<URI, BlockSnapshot>> prepareSnapshotsForSession(List<BlockObject> sourceObjList, int sourceCount, int newTargetCount,
            String newTargetsName) {
        List<Map<URI, BlockSnapshot>> snapSessionSnapshots = new ArrayList<>();

        for (int i = 0; i < newTargetCount; i++) {
            int count = 0;
            Map<URI, BlockSnapshot> targetMap = new HashMap<>();
            for (BlockObject sourceObj : sourceObjList) {
                // Generate label here
                String snapsetLabel = String.format("%s-%s", newTargetsName, i + 1);
                String label = snapsetLabel;
                String rgName = sourceObj.getReplicationGroupInstance();
                if (NullColumnValueGetter.isNotNullValue(rgName)) {
                    // There can be multiple RGs in a CG, in such cases generate unique name
                    if (sourceObjList.size() > 1) {
                        label = String.format("%s-%s-%s", snapsetLabel, rgName, ++count);
                    } else {
                        label = String.format("%s-%s", snapsetLabel, rgName);
                    }
                } else if (sourceObjList.size() > 1) {
                    label = String.format("%s-%s", snapsetLabel, ++count);
                }

                BlockSnapshot blockSnapshot = prepareSnapshotForSession(sourceObj, snapsetLabel, label);
                targetMap.put(blockSnapshot.getId(), blockSnapshot);
            }
            snapSessionSnapshots.add(targetMap);
        }

        return snapSessionSnapshots;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSnapshotSession(BlockObject sourceObj, URI snapSessionURI,
            List<List<URI>> snapSessionSnapshotURIs, String copyMode, String taskId) {
        // Must be implemented by platform implementations for which this is supported.
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateLinkNewTargetsRequest(BlockObject snapSessionSourceObj, Project project, int newTargetsCount,
            String newTargetsName, String newTargetCopyMode) {

        // Validate the project tenant.
        TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, project.getTenantOrg().getURI());
        ArgValidator.checkEntity(tenant, project.getTenantOrg().getURI(), false);

        // Verify the user is authorized.
        BlockServiceUtils.verifyUserIsAuthorizedForRequest(project,
                BlockServiceUtils.getUserFromContext(_securityContext), _permissionsHelper);

        // Verify the new target count. There can be restrictions on the
        // number of targets that can be linked to the snapshot sessions
        // for a given source.
        verifyNewTargetCount(snapSessionSourceObj, newTargetsCount, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void linkNewTargetVolumesToSnapshotSession(BlockObject snapSessionSourceObj, BlockSnapshotSession snapSession,
            List<List<URI>> snapshotURIs, String copyMode, String taskId) {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateRelinkSnapshotSessionTargets(BlockObject snapSessionSourceObj, BlockSnapshotSession tgtSnapSession,
            Project project, List<URI> snapshotURIs, UriInfo uriInfo) {

        // Validate the project tenant.
        TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, project.getTenantOrg().getURI());
        ArgValidator.checkEntity(tenant, project.getTenantOrg().getURI(), false);

        // Verify the user is authorized.
        BlockServiceUtils.verifyUserIsAuthorizedForRequest(project,
                BlockServiceUtils.getUserFromContext(_securityContext), _permissionsHelper);

        // Verify that each target is currently linked to a block
        // snapshot session of the same source.
        URI currentSnapSessionSourceURI = null;
        for (URI snapshotURI : snapshotURIs) {
            BlockSnapshotSessionUtils.validateSnapshot(snapshotURI, uriInfo, _dbClient);
            List<BlockSnapshotSession> snaphotSessionsList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient,
                    BlockSnapshotSession.class, ContainmentConstraint.Factory.getLinkedTargetSnapshotSessionConstraint(snapshotURI));
            if (snaphotSessionsList.isEmpty()) {
                // The target is not linked to an active snapshot session.
                throw APIException.badRequests.relinkTargetNotLinkedToActiveSnapshotSession(snapshotURI.toString());
            }

            // A target can only be linked to a single session.
            BlockSnapshotSession snapshotSnapSession = snaphotSessionsList.get(0);

            if (snapshotSnapSession.hasConsistencyGroup()) {
                if (currentSnapSessionSourceURI == null) {
                    currentSnapSessionSourceURI = snapshotSnapSession.getConsistencyGroup();
                } else if (!snapshotSnapSession.getConsistencyGroup().equals(currentSnapSessionSourceURI)) {
                    // Not all targets to be re-linked are linked to a block
                    // snapshot session of the same source.
                    throw APIException.badRequests.relinkSnapshotSessionsNotOfSameSource();
                }
            } else {
                // Verify that the snapshot session for the target is the same
                // as that for the other targets to be re-linked.
                if (currentSnapSessionSourceURI == null) {
                    currentSnapSessionSourceURI = snapshotSnapSession.getParent().getURI();
                } else if (!snapshotSnapSession.getParent().getURI().equals(currentSnapSessionSourceURI)) {
                    // Not all targets to be re-linked are linked to a block
                    // snapshot session of the same source.
                    throw APIException.badRequests.relinkSnapshotSessionsNotOfSameSource();
                }
            }
        }

        // All targets to be re-linked are linked to an active block snapshot
        // session of the same source. Now make sure target snapshot session
        // has this same source.
        URI tgtSnapSessionSourceURI = tgtSnapSession.hasConsistencyGroup() ? tgtSnapSession.getConsistencyGroup()
                : tgtSnapSession.getParent().getURI();
        if (!tgtSnapSessionSourceURI.equals(currentSnapSessionSourceURI)) {
            throw APIException.badRequests.relinkTgtSnapshotSessionHasDifferentSource(currentSnapSessionSourceURI.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void relinkTargetVolumesToSnapshotSession(BlockObject snapSessionSourceObj, BlockSnapshotSession TgtSnapSession,
            List<URI> snapshotURIs, String taskId) {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateUnlinkSnapshotSessionTargets(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, Project project,
            Map<URI, Boolean> targetMap, UriInfo uriInfo) {

        // Validate the project tenant.
        TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, project.getTenantOrg().getURI());
        ArgValidator.checkEntity(tenant, project.getTenantOrg().getURI(), false);

        // Verify the user is authorized.
        BlockServiceUtils.verifyUserIsAuthorizedForRequest(project,
                BlockServiceUtils.getUserFromContext(_securityContext), _permissionsHelper);

        // Validate targets are for the passed session.
        Set<URI> snapshotURIs = targetMap.keySet();
        BlockSnapshotSessionUtils.validateSnapshotSessionTargets(snapSession, snapshotURIs, uriInfo, _dbClient);

        // Targets cannot be unlinked if they are exported as this
        // would make the data unavailable to the export host(s).
        for (URI snapshotURI : snapshotURIs) {
            URIQueryResultList queryResults = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getSnapshotExportGroupConstraint(snapshotURI), queryResults);
            if (queryResults.iterator().hasNext()) {
                throw APIException.badRequests.cantUnlinkExportedSnapshotSessionTarget(snapshotURI.toString());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlinkTargetVolumesFromSnapshotSession(BlockObject snapSessionSourceObj, BlockSnapshotSession snapSession,
            Map<URI, Boolean> snapshotDeletionMap, String taskId) {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateRestoreSnapshotSession(List<BlockObject> snapSessionSourceObjs, Project project) {
        // Validate the project tenant.
        TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, project.getTenantOrg().getURI());
        ArgValidator.checkEntity(tenant, project.getTenantOrg().getURI(), false);

        // Verify the user is authorized.
        BlockServiceUtils.verifyUserIsAuthorizedForRequest(project,
                BlockServiceUtils.getUserFromContext(_securityContext), _permissionsHelper);

        for (BlockObject snapSessionSourceObj : snapSessionSourceObjs) {
            if (URIUtil.isType(snapSessionSourceObj.getId(), Volume.class)) {
                // Make sure that we don't have some pending
                // operation against the volume.
                Volume sourceVolume = (Volume) snapSessionSourceObj;
                checkForPendingTasks(sourceVolume, sourceVolume.getTenant().getURI());

                // On some platforms it is not possible to restore an array snapshot
                // point-in-time copy to a source volume if the volume has active mirrors.
                verifyActiveMirrors(sourceVolume);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyActiveMirrors(Volume sourceVolume) {
        // By default, disallow if there are active mirrors on the volume.
        List<URI> activeMirrorsForSource = BlockServiceUtils.getActiveMirrorsForVolume(sourceVolume, _dbClient);
        if (!activeMirrorsForSource.isEmpty()) {
            throw APIException.badRequests.snapshotSessionSourceHasActiveMirrors(
                    sourceVolume.getLabel(), activeMirrorsForSource.size());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restoreSnapshotSession(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, String taskId) {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateDeleteSnapshotSession(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, Project project) {
        // Validate the project tenant.
        TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, project.getTenantOrg().getURI());
        ArgValidator.checkEntity(tenant, project.getTenantOrg().getURI(), false);

        // Verify the user is authorized.
        BlockServiceUtils.verifyUserIsAuthorizedForRequest(project,
                BlockServiceUtils.getUserFromContext(_securityContext), _permissionsHelper);

        // Verify no pending tasks on the snapshot session.
        if (URIUtil.isType(snapSessionSourceObj.getId(), Volume.class)) {
            // Make sure that we don't have some pending
            // operation against the snapshot session.
            checkForPendingTasks(snapSession, ((Volume) snapSessionSourceObj).getTenant().getURI());
        }

        // Verify the snapshot session has no linked targets.
        StringSet linkedTargetIds = snapSession.getLinkedTargets();
        if ((linkedTargetIds != null) && (!linkedTargetIds.isEmpty())) {
            List<URI> linkedTargetURIs = URIUtil.toURIList(linkedTargetIds);
            Iterator<BlockSnapshot> activeLinkedTargets = _dbClient.queryIterativeObjects(BlockSnapshot.class, linkedTargetURIs, true);
            if (activeLinkedTargets.hasNext()) {
                throw APIException.badRequests.canDeactivateSnapshotSessionWithLinkedTargets();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteSnapshotSession(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, String taskId,
            String deleteType) {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * Looks up controller dependency for given hardware
     * 
     * @param clazz controller interface
     * @param hw hardware name
     * 
     * @return A reference to the controller.
     */
    protected <T extends Controller> T getController(Class<T> clazz, String hw) {
        return _coordinator.locateService(clazz, BlockServiceApi.CONTROLLER_SVC,
                BlockServiceApi.CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockSnapshotSession> getSnapshotSessionsForSource(BlockObject sourceObj) {
        return CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, BlockSnapshotSession.class,
                ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(sourceObj.getId()));
    }

    @Override
    public List<BlockSnapshotSession> getSnapshotSessionsForConsistencyGroup(BlockConsistencyGroup group) {
        return CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, BlockSnapshotSession.class,
                ContainmentConstraint.Factory.getBlockSnapshotSessionByConsistencyGroup(group.getId()));
    }

    @Override
    public List<BlockSnapshotSession> getSnapshotSessionsBySessionInstance(String instance) {
        return CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, BlockSnapshotSession.class,
                AlternateIdConstraint.Factory.getBlockSnapshotSessionBySessionInstance(instance));
    }

    /**
     * Get the BlockSnapshotSessionApi implementation for the system with the passed URI.
     * 
     * @param systemURI The URI of a storage system.
     * 
     * @return The BlockSnapshotSessionApi implementation for the storage system.
     */
    protected BlockSnapshotSessionApi getImplementationForBackendSystem(URI systemURI) {
        StorageSystem srcSideBackendSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
        BlockSnapshotSessionApi snapSessionImpl = _blockSnapshotSessionMgr
                .getPlatformSpecificImplForSystem(srcSideBackendSystem);
        return snapSessionImpl;
    }
}

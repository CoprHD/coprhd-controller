/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.SecurityContext;

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
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.ResourceOnlyNameGenerator;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
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
    public List<BlockObject> getAllSourceObjectsForSnapshotSessionRequest(BlockObject sourceObj) {
        // TBD - Handle source objects in CGs.
        List<BlockObject> sourceObjList = new ArrayList<BlockObject>();
        sourceObjList.add(sourceObj);
        return sourceObjList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj, List<BlockObject> sourceObjList, Project project,
            String name, int newTargetsCount, String newTargetCopyMode, BlockFullCopyManager fcManager) {

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
                // Verify the operation is supported for ingested volumes.
                Volume sourceVolume = (Volume) sourceObj;
                VolumeIngestionUtil.checkOperationSupportedOnIngestedVolume(sourceVolume,
                        ResourceOperationTypeEnum.CREATE_SNAPSHOT_SESSION, _dbClient);

                // Verify the source is not an internal object.
                BlockServiceUtils.validateNotAnInternalBlockObject(sourceObj, false);

                // Verify that array snapshots are allowed.
                VirtualPool vpool = BlockSnapshotSessionUtils.querySnapshotSessionSourceVPool(sourceObj, _dbClient);
                int maxNumberOfArraySnapsForSource = vpool.getMaxNativeSnapshots();
                if (maxNumberOfArraySnapsForSource == 0) {
                    throw APIException.badRequests.maxNativeSnapshotsIsZero(vpool.getLabel());
                }

                // Verify the number of array snapshots does not exceed
                // the limit specified by the virtual pool.
                // TBD - In my mind this should be a bad request exception. Inherited from create snapshot.
                if (getNumNativeSnapshots(sourceVolume) >= maxNumberOfArraySnapsForSource) {
                    throw APIException.methodNotAllowed.maximumNumberSnapshotsReached();
                }

                // Check for duplicate name.
                checkForDuplicateSnapshotName(name, sourceVolume);

                // Verify the new target count. There can be restrictions on the
                // number of targets that can be linked to the snapshot sessions
                // for a given source.
                verifyNewTargetCount(sourceObj, newTargetsCount);
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
     * Counts and returns the number of arrays snapshots for
     * the passed volume.
     * 
     * TBD - Reconcile with implementation in AbstractBlockServiceApiImpl,
     * which needs to be updated to look at sessions instances not block
     * snapshot instances.
     * 
     * @param volume A reference to a snapshot source volume.
     * 
     * @return The number of array snapshots on a volume.
     */
    protected Integer getNumNativeSnapshots(Volume volume) {
        // The number of native array snapshots is determined by the
        // number of snapshots sessions for which the passed volume
        // is the source.
        List<BlockSnapshotSession> snapSessions = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient,
                BlockSnapshotSession.class, ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(volume.getId()));
        return snapSessions.size();
    }

    /**
     * Check if a array snapshot with the same name exists for the passed volume.
     * Note that we need to compare the passed name to the session label for
     * the volumes snapshot sessions because the actual session names can have
     * an appended suffix when the volume is in a CG with multiple volumes.
     * Also, we need to run the name through the generator, which is done
     * prior to setting the session label for a snapshot session.
     * 
     * TBD - Reconcile with implementation in AbstractBlockServiceApiImpl.
     * 
     * @param requestedName The name to verify.
     * @param volume A reference to a snapshot source volume.
     */
    protected void checkForDuplicateSnapshotName(String requestedName, Volume volume) {
        String sessionLabel = ResourceOnlyNameGenerator.removeSpecialCharsForName(requestedName, SmisConstants.MAX_SNAPSHOT_NAME_LENGTH);
        List<BlockSnapshotSession> snapSessions = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient,
                BlockSnapshotSession.class, ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(volume.getId()));
        for (BlockSnapshotSession snapSession : snapSessions) {
            if (sessionLabel.equals(snapSession.getSessionLabel())) {
                throw APIException.badRequests.duplicateLabel(requestedName);
            }
        }
    }

    /**
     * Verifies that the number of targets to be linked to a snapshot session
     * is valid for the specified source. Should be overridden to provide platform
     * specific behavior.
     * 
     * @param sourceObj A reference to the snapshot session source
     * @param newTargetsCount The number of new targets to be linked to a session.
     */
    protected void verifyNewTargetCount(BlockObject sourceObj, int newTargetsCount) {
        // NoOp by default.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockSnapshotSession> prepareSnapshotSessions(List<BlockObject> sourceObjList, String snapSessionLabel, int newTargetCount,
            List<URI> snapSessionURIs, Map<URI, List<URI>> snapSessionSnapshotMap, String taskId) {

        int sourceCount = 1;
        List<BlockSnapshotSession> snapSessions = new ArrayList<BlockSnapshotSession>();
        for (BlockObject sourceObj : sourceObjList) {
            // Attempt to create distinct labels here when creating sessions
            // for more than one source.
            String instanceLabel = snapSessionLabel;
            if (sourceObjList.size() > 1) {
                instanceLabel = String.format("%s-%s", snapSessionLabel, sourceCount++);
            }
            BlockSnapshotSession snapSession = prepareSnapshotSessionFromSource(sourceObj, snapSessionLabel, instanceLabel, taskId);

            // If new targets are to be created and linked to the snapshot session, prepare
            // the BlockSnapshot instances to represent those targets.
            if (newTargetCount > 0) {
                List<URI> snapshotURIs = prepareSnapshotsForSession(newTargetCount, sourceObj, snapSessionLabel,
                        instanceLabel);
                StringSet linkedTargetIds = new StringSet();
                for (URI snapshotURI : snapshotURIs) {
                    linkedTargetIds.add(snapshotURI.toString());
                }
                snapSession.setLinkedTargets(linkedTargetIds);
                snapSessionSnapshotMap.put(snapSession.getId(), snapshotURIs);
            } else {
                snapSessionSnapshotMap.put(snapSession.getId(), new ArrayList<URI>());
            }

            // Update the snap sessions and URIs lists.
            snapSessionURIs.add(snapSession.getId());
            snapSessions.add(snapSession);
        }

        // Create and return the prepares snapshot sessions.
        _dbClient.createObject(snapSessions);
        return snapSessions;
    }

    /**
     * Prepare a ViPR BlockSnapshotSession instance for the passed source object.
     * 
     * @param sourceObj The snapshot session source.
     * @param snapSessionLabel The snapshot session label.
     * @param instanceLabel The unique snapshot session instance label.
     * @param taskId The unique task identifier.
     * 
     * @return
     */
    protected BlockSnapshotSession prepareSnapshotSessionFromSource(BlockObject sourceObj, String snapSessionLabel, String instanceLabel,
            String taskId) {
        BlockSnapshotSession snapSession = new BlockSnapshotSession();
        snapSession.setId(URIUtil.createId(BlockSnapshotSession.class));
        snapSession.setLabel(instanceLabel);
        snapSession.setSessionLabel(ResourceOnlyNameGenerator.removeSpecialCharsForName(snapSessionLabel,
                SmisConstants.MAX_SNAPSHOT_NAME_LENGTH));
        snapSession.setParent(new NamedURI(sourceObj.getId(), sourceObj.getLabel()));
        Project sourceProject = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(sourceObj, _dbClient);
        snapSession.setProject(new NamedURI(sourceProject.getId(), sourceObj.getLabel()));
        snapSession.setOpStatus(new OpStatusMap());
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CREATE_SNAPSHOT_SESSION);
        snapSession.getOpStatus().createTaskStatus(taskId, op);
        return snapSession;
    }

    /**
     * 
     * @param newTargetCount
     * @param sourceObj
     * @param snapSessionLabel
     * @param instanceLabel
     * 
     * @return
     */
    protected List<URI> prepareSnapshotsForSession(int newTargetCount, BlockObject sourceObj, String sessionLabel,
            String sessionInstanceLabel) {

        List<URI> snapshotURIs = new ArrayList<URI>();
        List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
        for (int i = 1; i <= newTargetCount; i++) {
            // Create distinct snapset and instance labels for each snapshot
            String snapsetLabel = sessionLabel;
            String snapshotLabel = sessionInstanceLabel;
            if (newTargetCount > 1) {
                snapsetLabel = String.format("%s-%s", sessionLabel, i);
                snapshotLabel = String.format("%s-%s", sessionInstanceLabel, i);
            }

            BlockSnapshot snapshot = new BlockSnapshot();
            snapshot.setId(URIUtil.createId(BlockSnapshot.class));
            URI cgUri = sourceObj.getConsistencyGroup();
            if (cgUri != null) {
                snapshot.setConsistencyGroup(cgUri);
            }
            snapshot.setSourceNativeId(sourceObj.getNativeId());
            snapshot.setParent(new NamedURI(sourceObj.getId(), sourceObj.getLabel()));
            snapshot.setLabel(snapshotLabel);
            snapshot.setStorageController(sourceObj.getStorageController());
            snapshot.setVirtualArray(sourceObj.getVirtualArray());
            snapshot.setProtocol(new StringSet());
            snapshot.getProtocol().addAll(sourceObj.getProtocol());
            Project sourceProject = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(sourceObj, _dbClient);
            snapshot.setProject(new NamedURI(sourceProject.getId(), sourceObj.getLabel()));
            snapshot.setSnapsetLabel(ResourceOnlyNameGenerator.removeSpecialCharsForName(
                    snapsetLabel, SmisConstants.MAX_SNAPSHOT_NAME_LENGTH));
            snapshot.setTechnologyType(BlockSnapshot.TechnologyType.NATIVE.name());
            snapshotURIs.add(snapshot.getId());
            snapshots.add(snapshot);
        }
        _dbClient.createObject(snapshots);
        return snapshotURIs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSnapshotSession(BlockObject sourceObj, List<URI> snapSessionURIs,
            Map<URI, List<URI>> snapSessionSnapshotMap, String copyMode, String taskId) {
        // Must be implemented by platform implementations for which this is supported.
        APIException.methodNotAllowed.notSupported();
    }

    /**
     * Looks up controller dependency for given hardware
     * 
     * @param clazz controller interface
     * @param hw hardware name
     * @param <T>
     * 
     * @return
     */
    protected <T extends Controller> T getController(Class<T> clazz, String hw) {
        return _coordinator.locateService(clazz, BlockServiceApi.CONTROLLER_SVC,
                BlockServiceApi.CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
    }
}

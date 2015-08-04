/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.ResourceOnlyNameGenerator;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
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
            String name, boolean createInactive, int newTargetsCount, String newTargetCopyMode, BlockFullCopyManager fcManager) {

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
     * {@inheritDoc}
     */
    @Override
    public TaskList createSnapshotSession() {
        return new TaskList();
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
}

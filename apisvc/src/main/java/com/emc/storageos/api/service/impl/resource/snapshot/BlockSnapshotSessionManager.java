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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshotSession.CopyMode;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.block.SnapshotSessionCreateParam;
import com.emc.storageos.model.block.SnapshotSessionTargetsParam;
import com.emc.storageos.security.audit.AuditLogManager;

/**
 * 
 */
public class BlockSnapshotSessionManager {

    // Enumeration specifying the valid keys for the snapshot session implementations map.
    public enum SnapshotSessionImpl {
        dflt, vmax, vmax3, vnx, vnxe, hds, openstack, scaleio, xtremio, xiv, rp, vplex
    }

    // A reference to a database client.
    private final DbClient _dbClient;

    // A reference to the audit log manager.
    @SuppressWarnings("unused")
    private AuditLogManager _auditLogManager = null;

    // A reference to the snapshot session request.
    protected HttpServletRequest _request;

    // A reference to the URI information.
    private final UriInfo _uriInfo;

    // The supported block snapshot session API implementations
    private final Map<String, BlockSnapshotSessionApi> _snapshotSessionImpls = new HashMap<String, BlockSnapshotSessionApi>();

    // A reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionManager.class);

    /**
     * Constructor
     * 
     * @param dbClient A reference to a database client.
     * @param permissionsHelper A reference to a permission helper.
     * @param auditLogManager A reference to an audit log manager.
     * @param coordinator A reference to the coordinator.
     * @param placementManager A reference to the placement manager.
     * @param securityContext A reference to the security context.
     * @param uriInfo A reference to the URI info.
     * @param request A reference to the snapshot session request.
     * @param tenantsService A reference to the tenants service or null.
     */
    public BlockSnapshotSessionManager(DbClient dbClient, PermissionsHelper permissionsHelper,
            AuditLogManager auditLogManager, CoordinatorClient coordinator,
            SecurityContext securityContext, UriInfo uriInfo, HttpServletRequest request) {
        _dbClient = dbClient;
        _auditLogManager = auditLogManager;
        _uriInfo = uriInfo;
        _request = request;

        // Create snapshot session implementations.
        createPlatformSpecificImpls(coordinator, permissionsHelper, securityContext);
    }

    /**
     * Create all platform specific snapshot session implementations.
     * 
     * @param coordinator A reference to the coordinator.
     * @param permissionsHelper A reference to a permission helper.
     * @param securityContext A reference to the security context.
     */
    private void createPlatformSpecificImpls(CoordinatorClient coordinator, PermissionsHelper permissionsHelper,
            SecurityContext securityContext) {
        _snapshotSessionImpls.put(SnapshotSessionImpl.dflt.name(), new DefaultBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                permissionsHelper, securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.vmax.name(), new VMAXBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                permissionsHelper, securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.vmax3.name(), new VMAX3BlockSnapshotSessionApiImpl(_dbClient, coordinator,
                permissionsHelper, securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.vnx.name(), new VNXBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                permissionsHelper, securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.vnxe.name(), new VNXEBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                permissionsHelper, securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.hds.name(), new HDSBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                permissionsHelper, securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.openstack.name(), new OpenstackBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                permissionsHelper, securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.scaleio.name(), new ScaleIOBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                permissionsHelper, securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.xtremio.name(), new XtremIOBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                permissionsHelper, securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.xiv.name(), new XIVBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                permissionsHelper, securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.vplex.name(), new VPlexBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                permissionsHelper, securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.rp.name(), new RPBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                permissionsHelper, securityContext, this));
    }

    /**
     * Gets a specific platform implementation.
     * 
     * @param implType The specific implementation desired.
     * 
     * @return The platform specific snapshot session implementation.
     */
    public BlockSnapshotSessionApi getPlatformSpecificImpl(SnapshotSessionImpl implType) {
        return _snapshotSessionImpls.get(implType.name());
    }

    /**
     * 
     * @param sourceURI
     * @param param
     * @param fcManager
     */
    public TaskList createSnapshotSession(URI sourceURI, SnapshotSessionCreateParam param, BlockFullCopyManager fcManager) {
        s_logger.info("START create snapshot session for source {}", sourceURI);

        // Get the snapshot session label.
        String snapSessionLabel = param.getName();

        // Get whether or not the snapshot session should be created inactive.
        // Check if the request calls for activation of the snapshot session.
        boolean createInactive = param.getCreateInactive() == null ? Boolean.FALSE : param.getCreateInactive();

        // Get the target device information, if any.
        int newLinkedTargetsCount = 0;
        String newTargetsCopyMode = CopyMode.nocopy.name();
        SnapshotSessionTargetsParam linkedTargetsParam = param.getLinkedTargets();
        if (linkedTargetsParam != null) {
            newLinkedTargetsCount = linkedTargetsParam.getCount().intValue();
            newTargetsCopyMode = linkedTargetsParam.getCopyMode();
        }

        // Get the source Volume or BlockSnapshot.
        BlockObject snapSessionSourceObj = BlockSnapshotSessionUtils.validateSnapshotSessionSource(sourceURI, _uriInfo, _dbClient);

        // Get the project for the snapshot session source object.
        Project project = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(snapSessionSourceObj, _dbClient);

        // Get the platform specific block snapshot session implementation.
        BlockSnapshotSessionApi snapSessionApiImpl = determinePlatformSpecificImplForSource(snapSessionSourceObj);

        // Get the list of all block objects for which we need to
        // create snapshot sessions. For example, when creating a
        // snapshot session for a volume in a consistency group,
        // we may create snapshot sessions for all volumes in the
        // consistency group.
        List<BlockObject> snapSessionSourceObjList = snapSessionApiImpl.getAllSourceObjectsForSnapshotSessionRequest(snapSessionSourceObj);

        // Validate the create snapshot session request.
        snapSessionApiImpl.validateSnapshotSessionCreateRequest(snapSessionSourceObj, snapSessionSourceObjList, project, snapSessionLabel,
                createInactive, newLinkedTargetsCount, newTargetsCopyMode, fcManager);

        // TBD - Prepare snapshot sessions
        // TBD - Prepare tasks
        // TBD - Make platform specific create request
        // TBD - Create audit log

        // Create a unique task identifier.
        @SuppressWarnings("unused")
        String taskId = UUID.randomUUID().toString();

        // Create the snapshot sessions.
        TaskList taskList = snapSessionApiImpl.createSnapshotSession();
        s_logger.info("FINISH create snapshot session for source {}", sourceURI);
        return taskList;
    }

    /**
     * Determines and returns the platform specific snapshot session implementation.
     * 
     * @param sourceObj A reference to the snapshot session source.
     * 
     * @return The platform specific snapshot session implementation.
     */
    private BlockSnapshotSessionApi determinePlatformSpecificImplForSource(BlockObject sourceObj) {

        BlockSnapshotSessionApi snapSessionApi = null;
        if (BlockObject.checkForRP(_dbClient, sourceObj.getId())) {
            snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.rp.name());
        } else {
            VirtualPool vpool = BlockSnapshotSessionUtils.querySnapshotSessionSourceVPool(sourceObj, _dbClient);
            if (VirtualPool.vPoolSpecifiesHighAvailability(vpool)) {
                snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.vplex.name());
            } else {
                URI systemURI = sourceObj.getStorageController();
                StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemURI);
                String systemType = system.getSystemType();
                if (DiscoveredDataObject.Type.vmax.name().equals(systemType)) {
                    if (system.checkIfVmax3()) {
                        snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.vmax3.name());
                    } else {
                        snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.vmax.name());
                    }
                } else if (DiscoveredDataObject.Type.vnxblock.name().equals(systemType)) {
                    snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.vnx.name());
                } else if (DiscoveredDataObject.Type.vnxe.name().equals(systemType)) {
                    snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.vnxe.name());
                } else if (DiscoveredDataObject.Type.hds.name().equals(systemType)) {
                    snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.hds.name());
                } else if (DiscoveredDataObject.Type.openstack.name().equals(systemType)) {
                    snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.openstack.name());
                } else if (DiscoveredDataObject.Type.scaleio.name().equals(systemType)) {
                    snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.scaleio.name());
                } else if (DiscoveredDataObject.Type.xtremio.name().equals(systemType)) {
                    snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.xtremio.name());
                } else if (DiscoveredDataObject.Type.ibmxiv.name().equals(systemType)) {
                    snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.xiv.name());
                } else {
                    snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.dflt.name());
                }
            }
        }

        return snapSessionApi;
    }
}

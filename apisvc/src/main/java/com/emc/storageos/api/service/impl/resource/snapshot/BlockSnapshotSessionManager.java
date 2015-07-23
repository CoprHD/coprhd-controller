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
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshotSession.CopyMode;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.block.SnapshotSessionCreateParam;
import com.emc.storageos.model.block.SnapshotSessionExistingTargetParam;
import com.emc.storageos.model.block.SnapshotSessionLinkedTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionNewTargetsParam;
import com.emc.storageos.security.audit.AuditLogManager;

/**
 * 
 */
public class BlockSnapshotSessionManager {
    
    // Enumeration specifying the valid keys for the snapshot session implementations map. 
    private enum SnapshotSessionImpl {
        dflt, vmax, vmax3, vnx, vnxe, hds, openstack, scaleio, xtremio, xiv, rp, vplex
    }
    
    // A reference to a database client.
    private DbClient _dbClient;
    
    // A reference to a permissions helper.
    private PermissionsHelper _permissionsHelper = null;
    
    // A reference to the audit log manager.
    @SuppressWarnings("unused")
    private AuditLogManager _auditLogManager = null;
    
    // A reference to the snapshot session request.
    protected HttpServletRequest _request;

    // A reference to the security context
    private SecurityContext _securityContext;

    // A reference to the URI information.
    private UriInfo _uriInfo;
    
    // The supported block snapshot session API implementations
    private Map<String, BlockSnapshotSessionApi> _snapshotSessionImpls = new HashMap<String, BlockSnapshotSessionApi>();

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
        _permissionsHelper = permissionsHelper;
        _auditLogManager = auditLogManager;
        _securityContext = securityContext;
        _uriInfo = uriInfo;
        _request = request;
        
        // Create snapshot session implementations.
        createPlatformSpecificImpls(coordinator);
    }
    
    /**
     * Create all platform specific snapshot session implementations.
     * 
     * @param coordinator A reference to the coordinator.
     */
    private void createPlatformSpecificImpls(CoordinatorClient coordinator) {
        _snapshotSessionImpls.put(SnapshotSessionImpl.dflt.name(), new DefaultBlockSnapshotSessionApiImpl(_dbClient, coordinator));
        _snapshotSessionImpls.put(SnapshotSessionImpl.vmax3.name(), new VMAX3BlockSnapshotSessionApiImpl(_dbClient, coordinator));
        _snapshotSessionImpls.put(SnapshotSessionImpl.hds.name(), new HDSBlockSnapshotSessionApiImpl(_dbClient, coordinator));
        _snapshotSessionImpls.put(SnapshotSessionImpl.vplex.name(), new VPlexBlockSnapshotSessionApiImpl(_dbClient, coordinator));
    }
    
    /**
     * 
     * @param sourceURI
     * @param param
     * @param fcManager
     */
    public TaskList createSnapshotSession(URI sourceURI, SnapshotSessionCreateParam param, BlockFullCopyManager fcManager) {
        s_logger.info("START create snapshot session for source {}", sourceURI);
        
        // Validate and get the source Volume or BlockSnapshot.
        BlockObject snapSessionSourceObj = BlockSnapshotSessionUtils.validateSnapshotSessionSource(sourceURI, _uriInfo, _dbClient);
        
        // Get the snapshot session label.
        String snapSessionLabel = param.getName();
        
        // Get whether or not the snapshot session should be created inactive.
        // Check if the request calls for activation of the snapshot session.
        boolean createInactive = param.getCreateInactive() == null ? Boolean.FALSE : param.getCreateInactive();
        
        // Get the target device information, if any.
        int newLinkedTargetsCount = 0;
        String newTargetsCopyMode = CopyMode.nocopy.name();
        Map<URI, String> existingTargetsMap = new HashMap<URI, String>();
        SnapshotSessionLinkedTargetsParam linkedTargetsParam = param.getLinkedTargets();
        if (linkedTargetsParam != null) {
            SnapshotSessionNewTargetsParam newTargetsParam = linkedTargetsParam.getNewTargets();
            if (newTargetsParam != null) {
                newLinkedTargetsCount = newTargetsParam.getCount().intValue();
                newTargetsCopyMode = newTargetsParam.getCopyMode();
            } else {
                // We only look at existing targets when new target info
                // is not specified in the request.
                List<SnapshotSessionExistingTargetParam> existingTargetsParam = linkedTargetsParam.getExistingTargets();
                if ((existingTargetsParam != null) && (!existingTargetsParam.isEmpty())) {
                    for (SnapshotSessionExistingTargetParam existingTargetParam : existingTargetsParam) {
                        existingTargetsMap.put(existingTargetParam.getVolume(), existingTargetParam.getCopyMode());
                    }
                }
            }
        }
        
        // Get the project for the snapshot session source object.
        Project project = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(snapSessionSourceObj, _dbClient);
      
        // Get the platform specific block snapshot session implementation.
        BlockSnapshotSessionApi snapSessionApiImpl = getPlatformSpecificImpl(snapSessionSourceObj);
        
        // Get the list of all block objects for which we need to
        // create snapshot sessions. For example, when creating a 
        // snapshot session for a volume in a consistency group, 
        // we may create snapshot sessions for all volumes in the 
        // consistency group.
        List<BlockObject> snapSessionSourceObjList = snapSessionApiImpl.getAllSourceObjectsForSnapshotSessionRequest(snapSessionSourceObj);
        
        // Validate the create snapshot session request.
        validateSnapshotSessionCreateRequest(snapSessionSourceObj,
            snapSessionSourceObjList, project, snapSessionLabel, createInactive,
            newLinkedTargetsCount, newTargetsCopyMode, existingTargetsMap,
            snapSessionApiImpl, fcManager);
        
        // prepare snapshots
        // tasks
        // execute
        // audit

        // Create a unique task identifier. 
        @SuppressWarnings("unused")
        String taskId = UUID.randomUUID().toString();
        
        // Create the snapshot sessions.
        TaskList taskList = snapSessionApiImpl.createSnapshotSession();
        s_logger.info("FINISH create snapshot session for source {}", sourceURI);
        return taskList;
    }
    
    /**
     * Validates the snapshot session creation request.
     * 
     * @param sourceObjList A list of snapshot session sources.
     * @param project A reference to the project.
     * @param snapSessionLabel The desired name for the snapshot session.
     * @param createInactive true if the session is to activated when created, false otherwise.
     * @param newTargetsCount The number of new targets to be created and linked to the snapshot session.
     * @param newTargetCopyMode The copy mode for new targets.
     * @param existingTargetsMap The existing targets to be linked to the snapshot session and copy mode for each.
     * @param snapSessionApiImpl A reference to the platform specific implementation.
     */
    private void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj, List<BlockObject> sourceObjList,
        Project project, String name, boolean createInactive, int newTargetsCount, String newTargetCopyMode, Map<URI, String> existingTargetsMap,
        BlockSnapshotSessionApi snapSessionApiImpl, BlockFullCopyManager fcManager) {
        
        // hds exported
            // abstract impl
                // vmax specific check for meta source object in CG for 4.x provider
                // calls full copy manager to validate full copy issues.
            // rp
            // rpvplex - same as rp.
        
        
        // Validate the project tenant.
        TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, project.getTenantOrg().getURI());
        ArgValidator.checkEntity(tenant, project.getTenantOrg().getURI(), false);
        
        // Verify the user is authorized.
        BlockServiceUtils.verifyUserIsAuthorizedForRequest(project,
            BlockServiceUtils.getUserFromContext(_securityContext), _permissionsHelper);
        
        // Verify the source objects.
        for (BlockObject sourceObj : sourceObjList) {
            // The full copy source object id.
            URI sourceURI = sourceObj.getId();
            if (URIUtil.isType(sourceURI, Volume.class)) {
                // Verify the operation is supported for ingested volumes.
                VolumeIngestionUtil.checkOperationSupportedOnIngestedVolume((Volume) sourceObj,
                    ResourceOperationTypeEnum.CREATE_SNAPSHOT_SESSION, _dbClient);
            }
            
            // Verify the source is not an internal object.
            BlockServiceUtils.validateNotAnInternalBlockObject(sourceObj, false);
        }
        
        // Verify the requested name.
        ArgValidator.checkFieldNotEmpty(name, "name");
        
        // TBD verify target device info pass to platform specific
        // Verify copy mode value for new targets is valid
        // Verify new linked target count does not exceed max, platform specific
        // Verify existing targets URIs reference valid existing volumes for snapshot targets, platform specific.
        // Verify copy mode for existing targets.
               
        // Platform specific checks
        snapSessionApiImpl.validateSnapshotSessionCreateRequest(requestedSourceObj, sourceObjList, name, fcManager);
    }
    
    /**
     * Determines and returns the platform specific snapshot session implementation.
     * 
     * @param sourceObj A reference to the snapshot session source.
     * 
     * @return The platform specific snapshot session implementation.
     */
    private BlockSnapshotSessionApi getPlatformSpecificImpl(BlockObject sourceObj) {
        
        BlockSnapshotSessionApi snapSessionApi = null;
        if (BlockObject.checkForRP(_dbClient, sourceObj.getId())) {
            snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.rp.name());
        } else  {
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

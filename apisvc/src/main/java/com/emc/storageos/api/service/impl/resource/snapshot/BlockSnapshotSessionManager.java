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
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.block.SnapshotSessionCreateParam;
import com.emc.storageos.security.audit.AuditLogManager;

/**
 * 
 */
public class BlockSnapshotSessionManager {
    
    // Enumeration specifying the valid keys for the full copy implementations map. 
    private enum SnapshotSessionImpl {
        dflt, vmax3, hds, vplex
    }
    
    // A reference to a database client.
    private DbClient _dbClient;
    
    // A reference to a permissions helper.
    @SuppressWarnings("unused")
    private PermissionsHelper _permissionsHelper = null;
    
    // A reference to the audit log manager.
    @SuppressWarnings("unused")
    private AuditLogManager _auditLogManager = null;
    
    // A reference to the full copy request.
    protected HttpServletRequest _request;

    // A reference to the security context
    @SuppressWarnings("unused")
    private SecurityContext _securityContext;

    // A reference to the URI information.
    @SuppressWarnings("unused")
    private UriInfo _uriInfo;
    
    // The supported block full copy API implementations
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
     * @param request A reference to the full copy request.
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
        createPlatformSpecificFullCopyImpls(coordinator);
    }
    
    /**
     * Create all platform specific snapshot session implementations.
     * 
     * @param coordinator A reference to the coordinator.
     */
    private void createPlatformSpecificFullCopyImpls(CoordinatorClient coordinator) {
        _snapshotSessionImpls.put(SnapshotSessionImpl.dflt.name(), new DefaultBlockSnapshotSessionApiImpl(_dbClient, coordinator));
        _snapshotSessionImpls.put(SnapshotSessionImpl.vmax3.name(), new VMAX3BlockSnapshotSessionApiImpl(_dbClient, coordinator));
        _snapshotSessionImpls.put(SnapshotSessionImpl.hds.name(), new HDSBlockSnapshotSessionApiImpl(_dbClient, coordinator));
        _snapshotSessionImpls.put(SnapshotSessionImpl.vplex.name(), new VPlexBlockSnapshotSessionApiImpl(_dbClient, coordinator));
    }
    
    /**
     * 
     * @param sourceURI
     * @param param
     */
    public TaskList createSnapshotSession(URI sourceURI, SnapshotSessionCreateParam param) {
        s_logger.info("START create snapshot session for source {}", sourceURI);
        return new TaskList();
    }
}

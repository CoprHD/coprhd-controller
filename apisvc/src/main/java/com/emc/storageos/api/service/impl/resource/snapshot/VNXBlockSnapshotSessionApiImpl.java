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

import java.util.List;

import javax.ws.rs.core.SecurityContext;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * 
 */
public class VNXBlockSnapshotSessionApiImpl extends DefaultBlockSnapshotSessionApiImpl {
    /**
     * Private default constructor should not be called outside class.
     */
    @SuppressWarnings("unused")
    private VNXBlockSnapshotSessionApiImpl() {
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
    public VNXBlockSnapshotSessionApiImpl(DbClient dbClient, CoordinatorClient coordinator, PermissionsHelper permissionsHelper,
            SecurityContext securityContext, BlockSnapshotSessionManager blockSnapshotSessionMgr) {
        super(dbClient, coordinator, permissionsHelper, securityContext, blockSnapshotSessionMgr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj, List<BlockObject> sourceObjList, Project project,
            String name, boolean createInactive, int newTargetsCount, String newTargetCopyMode, BlockFullCopyManager fcManager) {
        throw APIException.methodNotAllowed.notSupportedForVNX();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList createSnapshotSession() {
        throw APIException.methodNotAllowed.notSupportedForVNX();
    }
}

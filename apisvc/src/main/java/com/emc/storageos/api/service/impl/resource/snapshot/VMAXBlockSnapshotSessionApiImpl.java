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
import java.util.List;

import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * 
 */
public class VMAXBlockSnapshotSessionApiImpl extends DefaultBlockSnapshotSessionApiImpl {
    /**
     * Private default constructor should not be called outside class.
     */
    @SuppressWarnings("unused")
    private VMAXBlockSnapshotSessionApiImpl() {
        super();
    }

    /**
     * Constructor.
     * 
     * @param dbClient A reference to a data base client.
     * @param coordinator A reference to the coordinator client.
     */
    public VMAXBlockSnapshotSessionApiImpl(DbClient dbClient, CoordinatorClient coordinator) {
        super(dbClient, coordinator);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockObject> getAllSourceObjectsForSnapshotSessionRequest(BlockObject sourceObj) {
        throw APIException.methodNotAllowed.notSupportedForVMAX();        
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj,
        List<BlockObject> sourceObjList, String name, BlockFullCopyManager fcManager) {
        throw APIException.methodNotAllowed.notSupportedForVMAX();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList createSnapshotSession() {
        throw APIException.methodNotAllowed.notSupportedForVMAX();
    }
    
    /**
     * TBD Would have to call from validate if we support creating snapshot sessions
     * for VMAX2 platform. This could only happen if the request specifies creating a single
     * target volume. Right now, oit's not supported.
     * 
     * TBD Reconcile with same code in AbstractBlockServceApiImpl
     * 
     * @param requestedSourceObhj
     * @param sourceObjList
     */
    @SuppressWarnings("unused")
    private void verifyMetaVolumeInCG(BlockObject requestedSourceObj, List<BlockObject> sourceObjList) {
        
        // We should validate this for 4.x provider as it doesn't support snaps
        // for SRDF meta volumes.
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, requestedSourceObj.getStorageController());
        if (!system.getUsingSmis80()) {
            // Verify that if the volume is a member of vmax consistency group
            // all volumes in the group are regular volumes, not meta volumes.
            URI cgURI = requestedSourceObj.getConsistencyGroup();
            if (!NullColumnValueGetter.isNullURI(cgURI)) {
                BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
                for (BlockObject srcObject : sourceObjList) {
                    URI srcObjectURI = srcObject.getId();
                    if ((URIUtil.isType(srcObjectURI, Volume.class)) && (((Volume)srcObject).getIsComposite())) {
                        throw APIException.methodNotAllowed.notSupportedWithReason(String.format(
                            "Volume %s is a member of vmax consistency group which has meta volumes.", requestedSourceObj.getLabel()));
                    } else {
                        // TBD handle BlockSnapshot?
                    }
                }
            }
        }
    }
}

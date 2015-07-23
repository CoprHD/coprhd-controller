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
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * 
 */
public class HDSBlockSnapshotSessionApiImpl extends DefaultBlockSnapshotSessionApiImpl {

    /**
     * Private default constructor should not be called outside class.
     */
    @SuppressWarnings("unused")
    private HDSBlockSnapshotSessionApiImpl() {
        super();
    }

    /**
     * Constructor.
     * 
     * @param dbClient A reference to a data base client.
     * @param coordinator A reference to the coordinator client.
     */
    public HDSBlockSnapshotSessionApiImpl(DbClient dbClient, CoordinatorClient coordinator) {
        super(dbClient, coordinator);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj,
        List<BlockObject> sourceObjList, String name, BlockFullCopyManager fcManager) {
        // Call super.
        super.validateSnapshotSessionCreateRequest(requestedSourceObj, sourceObjList, name, fcManager);
        
        // TBD Not sure if this is required is no targets are being created
        // and linked to the array snapshot.
        for (BlockObject srcObject : sourceObjList) {
            URI srcObjectURI = srcObject.getId();
            if (URIUtil.isType(srcObjectURI, Volume.class)) {
                validateSourceVolumeExported((Volume)srcObject);
            }
        }
    }

    
    /**
     * Determines if the passed volume is an exported HDS volume and if not
     * throws an bad request APIException
     * 
     * TBD Reconcile with function in BlockService
     * 
     * @param volume A reference to a volume.
     */
    private void validateSourceVolumeExported(Volume volume) {
        if (!volume.isVolumeExported(_dbClient)) {
            throw APIException.badRequests.sourceNotExported(volume.getId());
        }
    }
}

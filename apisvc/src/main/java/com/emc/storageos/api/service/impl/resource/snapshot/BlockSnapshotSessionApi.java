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

import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.model.TaskList;


public interface BlockSnapshotSessionApi {
    
    /**
     * Get a list of all block objects to be operated on given the passed
     * snapshot session source object for a snapshot session request.
     * 
     * @param sourceObj A reference to a Volume or BlockSnapshot instance.
     * 
     * @return A list of all snapshot session source objects.
     */
    public List<BlockObject> getAllSourceObjectsForSnapshotSessionRequest(BlockObject sourceObj);
    
    /**
     * 
     * @param requestedSourceObj
     * @param sourceObjList
     * @param name
     * @param fcManager
     */
    public void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj,
        List<BlockObject> sourceObjList, String name, BlockFullCopyManager fcManager);
    
    /**
     * Creates a new block snapshot session.
     * 
     * @return TaskList
     */
    public TaskList createSnapshotSession();
}

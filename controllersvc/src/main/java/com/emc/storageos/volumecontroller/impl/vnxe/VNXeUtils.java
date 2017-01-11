/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vnxe;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.workflow.WorkflowService;

public class VNXeUtils {
    private static final Logger logger = LoggerFactory.getLogger(VNXeUtils.class);

    /**
     * Acquire the workflow step lock for CG
     * 
     * @param system The storage system instance
     * @param cgName The consistency group name
     * @param stepId The step id
     */
    public static void getCGLock(WorkflowService workflowService, StorageSystem system, String cgName, String stepId) {
        logger.info(String.format("Getting lock for the CG %s", cgName));
        List<String> lockKeys = new ArrayList<String>();
        String key = cgName + system.getNativeGuid();
        lockKeys.add(key);
        boolean lockAcquired = workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.ARRAY_CG));
        if (!lockAcquired) {
            throw DeviceControllerException.exceptions.failedToAcquireLock(lockKeys.toString(),
                    String.format("modify Unity consistency group %s", cgName));
        }
    }

    /**
     * Get block object consistency group name if the block object is in a consistency group; otherwise, it
     * returns null.
     * 
     * @param blockObject Volume or snapshot
     * @param dbClient The dbClient instance
     * @return The consistency group name
     */
    public static String getBlockObjectCGName(BlockObject blockObject, DbClient dbClient) {
        String cgName = blockObject.getReplicationGroupInstance();
        if (NullColumnValueGetter.isNotNullValue(cgName)) {
            if (blockObject instanceof BlockSnapshot) {
                BlockSnapshot snap = (BlockSnapshot) blockObject;
                URI volURI = snap.getParent().getURI();
                Volume parentVol = dbClient.queryObject(Volume.class, volURI);
                if (parentVol != null && !parentVol.getInactive()) {
                    cgName = parentVol.getReplicationGroupInstance();
                } else {
                    logger.error("The snapshot parent volume does not exist");
                }
            }
        } else {
            cgName = null;
        }
        return cgName;
    }
}

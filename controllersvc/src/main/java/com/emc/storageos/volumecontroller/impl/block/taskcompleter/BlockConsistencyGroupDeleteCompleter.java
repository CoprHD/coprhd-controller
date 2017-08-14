/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.workflow.WorkflowService;

public class BlockConsistencyGroupDeleteCompleter extends BlockConsistencyGroupTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(BlockConsistencyGroupDeleteCompleter.class);
    public static final String CONSISTENCY_GROUP_DELETED_MSG = "Consistency group %s deleted";
    public static final String CONSISTENCY_GROUP_DELETE_FAILED = "Failed to delete consistency group %s";

    private URI storageSystem;
    private String replicationGroupName;
    private boolean keepRGName = false;
    private boolean markInactive = false;

    public BlockConsistencyGroupDeleteCompleter(URI consistencyGroup, URI storageSystem, String replicationGroupName, boolean keepRGName,
            boolean markInactive, String opId) {
        super(BlockConsistencyGroup.class, consistencyGroup, opId);
        this.storageSystem = storageSystem;
        this.replicationGroupName = replicationGroupName;
        this.keepRGName = keepRGName;
        this.markInactive = markInactive;
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) {
        try {
            if (getConsistencyGroupURI() != null) {
                BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, getConsistencyGroupURI());
                boolean isRollback = WorkflowService.getInstance().isStepInRollbackState(getOpId());
                if (status == Operation.Status.error && isRollback) {
                    _log.error(String.format(
                            "Delete of Consistency group %s failed. Since this is during rollback, cleaning up the BlockConsistencyGroup object - "
                            + "replicationGroupName [%s], keepRGName [%s], markInactive [%s] ", consistencyGroup.getLabel(),
                            replicationGroupName, keepRGName, markInactive));
                    BlockConsistencyGroupUtils.cleanUpCGAndUpdate(consistencyGroup, storageSystem, replicationGroupName, markInactive, dbClient);
                }

                switch (status) {
                    case error:
                        if (isRollback && (coded instanceof ServiceError)) {
                            ServiceError error = (ServiceError) coded;
                            String originalMessage = error.getMessage();
                            String additionMessage = String.format(
                                    "Rollback encountered problems cleaning up consistency group %s on storage system %s and may require manual clean up",
                                    replicationGroupName, storageSystem.toString());
                            String updatedMessage = String.format("%s\n%s", originalMessage, additionMessage);
                            error.setMessage(updatedMessage);
                        }
                        dbClient.error(BlockConsistencyGroup.class, consistencyGroup.getId(), getOpId(),
                                coded);
                        break;
                    default:
                        dbClient.ready(BlockConsistencyGroup.class, consistencyGroup.getId(), getOpId());
                }
    
                recordBourneBlockConsistencyGroupEvent(dbClient, consistencyGroup.getId(), eventType(status),
                        status, eventMessage(status, consistencyGroup));

                if (status.equals(Operation.Status.ready) || (status.equals(Operation.Status.error) && isRollingBack())) {
                    dbClient.markForDeletion(consistencyGroup);
                }

            }
        } catch (Exception e) {
            _log.error("Failed updating status. BlockConsistencyGroupDelete {}, for task " + getOpId(), getId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    private RecordableEventManager.EventType eventType(Operation.Status status) {
        return (Operation.Status.ready == status) ?
                RecordableEventManager.EventType.ConsistencyGroupDeleted :
                RecordableEventManager.EventType.ConsistencyGroupDeleteFailed;
    }

    private String eventMessage(Operation.Status status, BlockConsistencyGroup consistencyGroup) {
        return (Operation.Status.ready == status) ?
                String.format(CONSISTENCY_GROUP_DELETED_MSG, consistencyGroup.getLabel()) :
                String.format(CONSISTENCY_GROUP_DELETE_FAILED, consistencyGroup.getLabel());
    }
}

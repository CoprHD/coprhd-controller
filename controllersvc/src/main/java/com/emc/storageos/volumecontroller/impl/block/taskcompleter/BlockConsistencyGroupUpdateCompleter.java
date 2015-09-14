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
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;

public class BlockConsistencyGroupUpdateCompleter extends BlockConsistencyGroupTaskCompleter {
    private static final Logger _log =
            LoggerFactory.getLogger(BlockConsistencyGroupUpdateCompleter.class);
    public static final String CONSISTENCY_GROUP_UPDATED_MSG =
            "Consistency group %s updated.";
    public static final String CONSISTENCY_GROUP_UPDATE_FAILED_MSG =
            "Failed to update consistency group  %s";

    public BlockConsistencyGroupUpdateCompleter(URI consistencyGroup, String opId) {
        super(BlockConsistencyGroup.class, consistencyGroup, opId);
    }

    @Override
    public void ready(DbClient dbClient) throws DeviceControllerException {
        try {
            super.ready(dbClient);
            BlockConsistencyGroup consistencyGroup =
                    dbClient.queryObject(BlockConsistencyGroup.class,
                            getConsistencyGroupURI());

            dbClient.ready(BlockConsistencyGroup.class, consistencyGroup.getId(),
                    getOpId());

            recordBourneBlockConsistencyGroupEvent(dbClient, consistencyGroup.getId(),
                    eventType(Status.ready), Status.ready, eventMessage(Status.ready,
                            consistencyGroup));
        } catch (Exception e) {
            _log.error("Failed updating status. BlockConsistencyGroupUpdate {}, for task "
                    + getOpId(), getId(), e);
        }
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded)
            throws DeviceControllerException {
        try {
            super.complete(dbClient, status, coded);
            BlockConsistencyGroup consistencyGroup =
                    dbClient.queryObject(BlockConsistencyGroup.class,
                            getConsistencyGroupURI());

            dbClient.ready(BlockConsistencyGroup.class, consistencyGroup.getId(),
                    getOpId());

            recordBourneBlockConsistencyGroupEvent(dbClient, consistencyGroup.getId(),
                    eventType(Status.ready), Status.ready, eventMessage(Status.ready,
                    consistencyGroup));
        } catch (Exception e) {
            _log.error("Failed updating status. BlockConsistencyGroupUpdate {}, for task "
                    + getOpId(), getId(), e);
        }
    }

    @Override
    public void error(DbClient dbClient, ServiceCoded serviceCoded) throws
            DeviceControllerException {
        try {
            super.error(dbClient, serviceCoded);
            BlockConsistencyGroup consistencyGroup =
                    dbClient.queryObject(BlockConsistencyGroup.class,
                            getConsistencyGroupURI());

            dbClient.error(BlockConsistencyGroup.class, consistencyGroup.getId(),
                    getOpId(), serviceCoded);

            recordBourneBlockConsistencyGroupEvent(dbClient, consistencyGroup.getId(),
                    eventType(Status.error), Status.error, eventMessage(Status.error,
                            consistencyGroup));
        } catch (Exception e) {
            _log.error("Failed updating status. BlockConsistencyGroupUpdate {}, " +
                    "for task " + getOpId(), getId(), e);
        }
    }

    private RecordableEventManager.EventType eventType(Status status) {
        return (Status.ready == status) ?
                RecordableEventManager.EventType.ConsistencyGroupUpdated :
                RecordableEventManager.EventType.ConsistencyGroupUpdateFailed;
    }

    private String eventMessage(Status status, BlockConsistencyGroup consistencyGroup) {
        return (Status.ready == status) ?
                String.format(CONSISTENCY_GROUP_UPDATED_MSG, consistencyGroup.getLabel()) :
                String.format(CONSISTENCY_GROUP_UPDATE_FAILED_MSG,
                        consistencyGroup.getLabel());
    }

}
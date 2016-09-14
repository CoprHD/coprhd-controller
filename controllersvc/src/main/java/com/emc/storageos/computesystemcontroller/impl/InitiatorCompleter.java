/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.util.ExportUtils;

public class InitiatorCompleter extends ComputeSystemCompleter {

    private static final Logger _logger = LoggerFactory.getLogger(InitiatorCompleter.class);
    private static final long serialVersionUID = 1L;
    private URI eventId;

    public InitiatorCompleter(URI eventId, URI id, boolean deactivateOnComplete, String opId) {
        super(Initiator.class, id, deactivateOnComplete, opId);
        this.eventId = eventId;
    }

    public InitiatorCompleter(URI eventId, List<URI> ids, boolean deactivateOnComplete, String opId) {
        super(Initiator.class, ids, deactivateOnComplete, opId);
        this.eventId = eventId;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        super.complete(dbClient, status, coded);
        for (URI id : getIds()) {
            switch (status) {
                case error:
                    dbClient.error(Initiator.class, this.getId(), getOpId(), coded);
                    if (!NullColumnValueGetter.isNullURI(eventId)) {
                        ActionableEvent event = dbClient.queryObject(ActionableEvent.class, eventId);
                        if (event != null) {
                            event.setEventStatus(ActionableEvent.Status.failed.name());
                            dbClient.updateObject(event);
                        }
                    }
                    break;
                default:
                    dbClient.ready(Initiator.class, this.getId(), getOpId());
            }

            if (deactivateOnComplete && status.equals(Status.ready)) {
                Initiator initiator = dbClient.queryObject(Initiator.class, id);
                Initiator associatedInitiator = ExportUtils.getAssociatedInitiator(initiator, dbClient);
                dbClient.markForDeletion(initiator);
                if (associatedInitiator != null) {
                    dbClient.markForDeletion(associatedInitiator);
                }
                _logger.info("Initiator marked for deletion: " + this.getId());
            }
        }
    }
}

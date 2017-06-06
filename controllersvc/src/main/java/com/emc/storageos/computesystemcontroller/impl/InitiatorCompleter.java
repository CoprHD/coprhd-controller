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

public class InitiatorCompleter extends ComputeSystemCompleter {

    private static final Logger _logger = LoggerFactory.getLogger(InitiatorCompleter.class);
    private static final long serialVersionUID = 1L;
    private final URI eventId;
    private final InitiatorOperation op;

    public enum InitiatorOperation {
        ADD, REMOVE;
    }

    public InitiatorCompleter(URI eventId, URI id, InitiatorOperation op, String opId) {
        super(Initiator.class, id, opId);
        this.op = op;
        this.eventId = eventId;
    }

    public InitiatorCompleter(URI eventId, List<URI> ids, InitiatorOperation op, String opId) {
        super(Initiator.class, ids, opId);
        this.op = op;
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

                    // If this completer is part of an add initiator operation and the operation
                    // is NOT successful, we want to remove the initiator. The initiator should only
                    // be removed if it is no longer in use.
                    if (op == InitiatorOperation.ADD) {
                        removeInitiator(id, dbClient);
                    }

                    break;
                default:
                    dbClient.ready(Initiator.class, this.getId(), getOpId());
                    // If this completer is part of a remove initiator operation and the operation
                    // is successful, we want to remove the initiator. The initiator should only
                    // be removed if it is no longer in use.
                    if (op == InitiatorOperation.REMOVE) {
                        removeInitiator(id, dbClient);
                    }
            }
        }
    }

    /**
     * Removes an initiator if it is no longer in use.
     *
     * @param initiatorUri the initiator to remove
     * @param dbClient the DB client
     */
    private void removeInitiator(URI initiatorUri, DbClient dbClient) {
        if (!ComputeSystemHelper.isInitiatorInUse(dbClient, initiatorUri.toString()) 
                && (op == InitiatorOperation.REMOVE || NullColumnValueGetter.isNullURI(eventId))) {
            Initiator initiator = dbClient.queryObject(Initiator.class, initiatorUri);
            dbClient.markForDeletion(initiator);
            _logger.info("Initiator marked for deletion: " + this.getId());
        }
    }
}

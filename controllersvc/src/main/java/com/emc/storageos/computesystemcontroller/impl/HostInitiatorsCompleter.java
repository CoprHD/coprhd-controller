package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.computesystemcontroller.impl.InitiatorCompleter.InitiatorOperation;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class HostInitiatorsCompleter extends ComputeSystemCompleter {

    private static final Logger _logger = LoggerFactory.getLogger(HostInitiatorsCompleter.class);
    private static final long serialVersionUID = 1L;
    private final URI eventId;
    private List<URI> newInitiators;
    private List<URI> oldInitiators;

    public HostInitiatorsCompleter(URI eventId, URI hostId, List<URI> newInitiators, List<URI> oldInitiators, String opId) {
        super(Host.class, hostId, opId);
        this.eventId = eventId;
        this.newInitiators = newInitiators;
        this.oldInitiators = oldInitiators;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        super.complete(dbClient, status, coded);

        InitiatorCompleter newInitiatorCompleter = new InitiatorCompleter(eventId, newInitiators, InitiatorOperation.ADD, this.getOpId());
        InitiatorCompleter oldInitiatorCompleter = new InitiatorCompleter(eventId, oldInitiators, InitiatorOperation.REMOVE,
                this.getOpId());

        switch (status) {
            case error:
                dbClient.error(Host.class, this.getId(), getOpId(), coded);
                if (!NullColumnValueGetter.isNullURI(eventId)) {
                    ActionableEvent event = dbClient.queryObject(ActionableEvent.class, eventId);
                    if (event != null) {
                        event.setEventStatus(ActionableEvent.Status.failed.name());
                        dbClient.updateObject(event);
                    }
                }
                break;
            default:
                dbClient.ready(Host.class, this.getId(), getOpId());
        }

        newInitiatorCompleter.complete(dbClient, status, coded);
        oldInitiatorCompleter.complete(dbClient, status, coded);
    }
}

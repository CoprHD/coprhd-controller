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
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class InitiatorCompleter extends ComputeSystemCompleter {
	
	private static final Logger _logger = LoggerFactory.getLogger(InitiatorCompleter.class);
	private static final long serialVersionUID = 1L;
	
	
	
	public InitiatorCompleter(URI id, boolean deactivateOnComplete, String opId) {
	    super(Initiator.class, id, deactivateOnComplete, opId);
	}
	
	public InitiatorCompleter(List<URI> ids, boolean deactivateOnComplete, String opId) {
		super(Initiator.class, ids, deactivateOnComplete, opId);
	}
	
	@Override
	protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
	    super.complete(dbClient,  status,  coded);
	    for (URI id : getIds()) {
		    switch (status) {
		        case error:
		            dbClient.error(Initiator.class, this.getId(), getOpId(), coded);
		            break;
		        default:
		            dbClient.ready(Initiator.class, this.getId(), getOpId());
		    }
		
		    if (deactivateOnComplete && status.equals(Status.ready)) {            
		        Initiator initiator = dbClient.queryObject(Initiator.class, id);
		        dbClient.markForDeletion(initiator);
		        _logger.info("Initiator marked for deletion: " + this.getId());
		    }
	    }
	}
}

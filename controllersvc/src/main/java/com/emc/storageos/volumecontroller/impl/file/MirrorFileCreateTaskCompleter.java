/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class MirrorFileCreateTaskCompleter extends MirrorFileTaskCompleter {
	
	@Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {

        super.complete(dbClient, status, coded);
    }

    private URI vpoolChangeURI;
	public MirrorFileCreateTaskCompleter(Class clazz, List<URI> ids, String opId) {
		super(clazz, ids, opId);

	}

	public MirrorFileCreateTaskCompleter(Class clazz, URI id, String opId) {
		super(clazz, id, opId);

	}

	public MirrorFileCreateTaskCompleter(URI sourceURI, URI targetURI,
			URI vPoolChangeUri, String opId) {
		super(sourceURI, targetURI, opId);
		if(vPoolChangeUri != null) {
		    vpoolChangeURI = vPoolChangeUri;
		}

	}

}

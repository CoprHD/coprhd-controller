/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class FileDeleteWorkflowCompleter extends FileWorkflowCompleter{

	public FileDeleteWorkflowCompleter(URI fsUri, String task) {
		super(fsUri, task);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void complete(DbClient dbClient, Status status,
			ServiceCoded serviceCoded) {
		// TODO Auto-generated method stub
		super.complete(dbClient, status, serviceCoded);
	}

	public FileDeleteWorkflowCompleter(List<URI> fsUris, String task) {
		super(fsUris, task);
		// TODO Auto-generated constructor stub
	}
}

/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;
import static java.util.Arrays.asList;

public class MirrorFileTaskCompleter extends TaskCompleter {

	public MirrorFileTaskCompleter(Class clazz, List<URI> ids, String opId) {
		super(clazz, ids, opId);
	}

	public MirrorFileTaskCompleter(Class clazz, URI id, String opId) {
		super(clazz, id, opId);
	}
	
	public MirrorFileTaskCompleter(URI sourceURI, URI targetURI, String opId) {
        super(FileShare.class, asList(sourceURI, targetURI), opId);
    }

	/**
     * Reference to logger
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(MirrorFileTaskCompleter.class);

    private DbClient dbClient;

    protected List<FileShare> fileshareCache;

	
	@Override
	protected void complete(DbClient dbClient, Status status, ServiceCoded coded)
			throws DeviceControllerException {
        setStatus(dbClient, status, coded);
        updateWorkflowStatus(status, coded);
        updateFileSystemStatus(dbClient, status);
	}
	
    protected void updateFileSystemStatus(DbClient dbClient, Operation.Status status) {
        try {
            if (Operation.Status.ready.equals(status)) {
                List<FileShare> fileshares = dbClient.queryObject(FileShare.class, getIds());
                for (FileShare fileshare : fileshares) {
                                    }
                dbClient.persistObject(fileshares);
                _logger.info("Updated Mirror link status for fileshares: {}", getIds());
            }
        } catch (Exception e) {
            _logger.info("Not updating file Mirror link status for fileshares: {}", getIds(), e);
        }
    }


}

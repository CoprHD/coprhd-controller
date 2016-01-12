/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Task completer invoked when SMI-S request to restore a snapshot session completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionRestoreCompleter extends BlockSnapshotSessionCompleter {

    /**
     * Constructor
     * 
     * @param snapSessionURI The id of the BlockSnapshotSession instance in the database.
     * @param stepId The id of the WF step in which the session is being restored.
     */
    public BlockSnapshotSessionRestoreCompleter(URI snapSessionURI, String stepId) {
        super(snapSessionURI, stepId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        super.complete(dbClient, status, coded);
    }

    @Override
    protected String getDescriptionOfResults(Status status, BlockObject sourceObj, BlockSnapshotSession snapSession) {
        return null;
    }
}
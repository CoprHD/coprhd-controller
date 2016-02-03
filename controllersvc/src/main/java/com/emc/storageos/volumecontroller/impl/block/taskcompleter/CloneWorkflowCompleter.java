/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Completer called when a full copy volumes operation completes.
 */
public class CloneWorkflowCompleter extends VolumeTaskCompleter {

    private static final long serialVersionUID = -8760349639300139009L;

    private static final Logger log = LoggerFactory
            .getLogger(CloneWorkflowCompleter.class);

    public CloneWorkflowCompleter(List<URI> fullCopyVolumeURIs, String task) {
        super(Volume.class, fullCopyVolumeURIs, task);
        setNotifyWorkflow(true);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        log.info("START CloneWorkflowCompleter complete");

        super.setStatus(dbClient, status, coded);
        super.complete(dbClient, status, coded);
        
        // clear VolumeGroup's Partial_Request Flag
        List<Volume> toUpdate = new ArrayList<Volume>();
        for (URI fullCopyURI : getIds()) {
            Volume fullCopy = dbClient.queryObject(Volume.class, fullCopyURI);
            if (fullCopy.checkInternalFlags(Flag.VOLUME_GROUP_PARTIAL_REQUEST)) {
                fullCopy.clearInternalFlags(Flag.VOLUME_GROUP_PARTIAL_REQUEST);
                toUpdate.add(fullCopy);
            }
        }
        if (!toUpdate.isEmpty()) {
            log.info("Clearing PARTIAL flag set on Clones for Partial request");
            dbClient.updateObject(toUpdate);
        }

        log.info("END CloneWorkflowCompleter complete");
    }
}
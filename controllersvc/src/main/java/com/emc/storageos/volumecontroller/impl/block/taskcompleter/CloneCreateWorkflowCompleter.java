/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Completer called when a create full copy volumes workflow completes.
 */
public class CloneCreateWorkflowCompleter extends VolumeTaskCompleter {

    private static final long serialVersionUID = -8760349639300139009L;

    private static final Logger log = LoggerFactory
            .getLogger(CloneCreateWorkflowCompleter.class);

    public CloneCreateWorkflowCompleter(List<URI> volumeURIs, String task) {
        super(Volume.class, volumeURIs, task);
        setNotifyWorkflow(true);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        log.info("START FullCopyVolumeCreateWorkflowCompleter complete");

        super.setStatus(dbClient, status, coded);
        super.complete(dbClient, status, coded);

        // clear VolumeGroup's Partial_Request Flag
        List<Volume> toUpdate = new ArrayList<Volume>();
        for (URI volumeURI : getIds()) {
            Volume volume = dbClient.queryObject(Volume.class, volumeURI);
            // Note that not all passed volumes are full copy volumes in the case
            // of VPLEX. The volumes will include the VPLEX full copy volumes,
            // the backend full copy volumes, and for distributed VPLEX full copy,
            // the HA side volumes will also be included. As such, we must check
            // that the associated source volume is not null to identify full copies.
            URI associatedSourceURI = volume.getAssociatedSourceVolume();
            if (!NullColumnValueGetter.isNullURI(associatedSourceURI)) {
                Volume source = dbClient.queryObject(Volume.class, associatedSourceURI);
                if (source != null && source.checkInternalFlags(Flag.VOLUME_GROUP_PARTIAL_REQUEST)) {
                    source.clearInternalFlags(Flag.VOLUME_GROUP_PARTIAL_REQUEST);
                    toUpdate.add(source);
                }
            }
            
            // On error we need to make sure we clean up the volumes prepared 
            // for this request.
            if (status == Operation.Status.error) {
                volume.setInactive(Boolean.TRUE);
                toUpdate.add(volume);
            }
        }
        if (!toUpdate.isEmpty()) {
            log.info("Clearing PARTIAL flag set on Volumes for Partial request");
            dbClient.updateObject(toUpdate);
        }

        log.info("END FullCopyVolumeCreateWorkflowCompleter complete");
    }
}
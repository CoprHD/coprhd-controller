/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class ExportMaskAddPathsCompleter extends ExportTaskCompleter{
    private static final org.slf4j.Logger _log = LoggerFactory.getLogger(ExportMaskAddPathsCompleter.class);
    private List<URI> newTargets;
    private List<URI> newInitiators;
    
    public ExportMaskAddPathsCompleter(URI id, URI emURI, String opId) {
        super(ExportGroup.class, id, emURI, opId);
    }

    public void setNewStoragePorts(List<URI> ports) {
        newTargets = ports;
    }
    
    public void setNewInitiators(List<URI>  initiators) {
        newInitiators = initiators;
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status,
            ServiceCoded coded) throws DeviceControllerException {
        try {
            if (status == Operation.Status.ready) {
                ExportMask exportMask = dbClient.queryObject(ExportMask.class, getMask());
                if (newTargets != null && !newTargets.isEmpty()) {
                    for (URI target : newTargets) {
                        exportMask.addTarget(target);
                    }
                }
                if(newInitiators != null && !newInitiators.isEmpty()) {
                    for (URI initiatorURI : newInitiators) {
                        Initiator initiator = dbClient.queryObject(Initiator.class, initiatorURI);
                        if (initiator != null && !initiator.getInactive()) {
                            exportMask.addInitiator(initiator);
                            exportMask.addToUserCreatedInitiators(initiator);
                        }
                    }
                }
                
                dbClient.updateObject(exportMask);
            }
        } catch (Exception e) {
            _log.error(String.format(
                    "Failed updating status for ExportMaskAddPaths - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }
}

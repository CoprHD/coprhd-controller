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
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class ExportMaskRemovePathsCompleter extends ExportTaskCompleter{
    private static final org.slf4j.Logger _log = LoggerFactory.getLogger(ExportMaskRemovePathsCompleter.class);
    private List<URI> removedTargets;
    private List<URI> removedInitiators;
    
    public ExportMaskRemovePathsCompleter(URI id, URI emURI, String opId) {
        super(ExportGroup.class, id, emURI, opId);
    }

    public void setRemovedStoragePorts(List<URI> ports) {
        removedTargets = ports;
    }
    
    public void setRemovedInitiators(List<URI>  initiators) {
        removedInitiators = initiators;
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status,
            ServiceCoded coded) throws DeviceControllerException {
        try {
            if (status == Operation.Status.ready) {
                ExportMask exportMask = dbClient.queryObject(ExportMask.class, getMask());
                if (removedTargets != null && !removedTargets.isEmpty()) {
                    for (URI target : removedTargets) {
                        exportMask.removeTarget(target);
                    }
                }
                if(removedInitiators != null && !removedInitiators.isEmpty()) {
                    exportMask.removeInitiatorURIs(removedInitiators);
                    exportMask.removeFromUserAddedInitiatorsByURI(removedInitiators);
                }
                
                dbClient.updateObject(exportMask);
            }
        } catch (Exception e) {
            _log.error(String.format(
                    "Failed updating status for ExportMaskRemovePaths - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

}

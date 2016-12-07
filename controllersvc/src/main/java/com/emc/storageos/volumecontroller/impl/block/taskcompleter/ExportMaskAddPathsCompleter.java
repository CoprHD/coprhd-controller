/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.util.StringSetUtil;
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
                // update storage ports
                    exportMask.getStoragePorts().addAll(StringSetUtil.uriListToSet(newTargets));
                }
                if(newInitiators != null && !newInitiators.isEmpty()) {
                    exportMask.getInitiators().addAll(StringSetUtil.uriListToSet(newInitiators));
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

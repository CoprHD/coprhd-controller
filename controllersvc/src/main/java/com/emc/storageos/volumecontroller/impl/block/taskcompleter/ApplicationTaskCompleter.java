/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Task completer for update application volumes
 *
 */
public class ApplicationTaskCompleter extends TaskCompleter{

    private static final long serialVersionUID = -9188670003331949130L;
    private static final Logger log = LoggerFactory.getLogger(ApplicationTaskCompleter.class);
    private List<URI> volumes;
    private List<URI> consistencyGroups;
    
    public ApplicationTaskCompleter(URI volumeGroupId, List<URI> volumes, List<URI> consistencyGroups, String opId) {
        super(VolumeGroup.class, volumeGroupId, opId);
        this.volumes = volumes; 
        this.consistencyGroups = consistencyGroups;
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        log.info("START ApplicationCompleter complete");
        super.setStatus(dbClient, status, coded);
        updateWorkflowStatus(status, coded);
        for (URI voluri : volumes) {
            switch (status) {
                case error:
                    setErrorOnDataObject(dbClient, Volume.class, voluri, coded);
                    break;
                default:
                    setReadyOnDataObject(dbClient, Volume.class, voluri);
            }
        }
        if (consistencyGroups != null && !consistencyGroups.isEmpty()) {
            for (URI cguri : consistencyGroups) {
                switch (status) {
                    case error:
                        setErrorOnDataObject(dbClient, BlockConsistencyGroup.class, cguri, coded);
                        break;
                    default:
                        setReadyOnDataObject(dbClient, BlockConsistencyGroup.class, cguri);
                }
            }
        }
        log.info("END ApplicationCompleter complete");
    }
}

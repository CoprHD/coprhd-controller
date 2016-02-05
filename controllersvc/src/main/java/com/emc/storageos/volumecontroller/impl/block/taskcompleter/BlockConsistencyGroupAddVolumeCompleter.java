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
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class BlockConsistencyGroupAddVolumeCompleter extends BlockConsistencyGroupUpdateCompleter{

    private static final long serialVersionUID = -871023109512730999L;
    private static final Logger log = LoggerFactory.getLogger(BlockConsistencyGroupAddVolumeCompleter.class);
    private List<URI> addVolumeList = null;
    String groupName;
    
    public BlockConsistencyGroupAddVolumeCompleter(URI cgURI, List<URI>addVolumesList, String groupName, String opId) {
        super(cgURI, opId);
        this.addVolumeList = addVolumesList;
        this.groupName = groupName;
    }
    
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded)
            throws DeviceControllerException {
        log.info("Updating add volume replicationGroupInstance");
        try {
            super.complete(dbClient, status, coded);
            if (status == Status.ready) {
                BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, getId());
                if (groupName == null) {
                    groupName = (cg.getAlternateLabel() != null) ? cg.getAlternateLabel() : cg.getLabel();
                }
                for (URI voluri : addVolumeList) {
                    Volume volume = dbClient.queryObject(Volume.class, voluri);
                    if (volume != null && !volume.getInactive()) {
                        volume.setReplicationGroupInstance(groupName);
                        volume.setConsistencyGroup(this.getConsistencyGroupURI());
                        dbClient.updateObject(volume);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed updating status. BlockConsistencyGroupRemoveVolume {}, for task "
                    + getOpId(), getId(), e);
        }
    }

}
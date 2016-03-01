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
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Task completer class for remove volumes from CG
 *
 */
public class BlockConsistencyGroupRemoveVolumeCompleter extends BlockConsistencyGroupUpdateCompleter{

    private static final long serialVersionUID = -871023109512730999L;
    private static final Logger log = LoggerFactory.getLogger(BlockConsistencyGroupRemoveVolumeCompleter.class);
    private List<URI> removedVolumeList = null;
    private boolean keepRGReference = false;
    
    public BlockConsistencyGroupRemoveVolumeCompleter(URI cgURI, List<URI>removedVolumesList, boolean keepRGReference, String opId) {
        super(cgURI, opId);
        this.removedVolumeList = removedVolumesList;
        this.keepRGReference = keepRGReference;
    }
    
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded)
            throws DeviceControllerException {
        log.info("Updating removed volume replicationGroupInstance");
        try {
            if (status == Status.ready && !keepRGReference) {
                for (URI blockObjectURI : removedVolumeList) {
                    BlockObject blockObject = BlockObject.fetch(dbClient, blockObjectURI);
                    if (blockObject != null) {
                        if (blockObject instanceof Volume) {
                            VolumeGroup volumeGroup = ((Volume) blockObject).getApplication(dbClient);
                            if (volumeGroup != null) {
                                ((Volume) blockObject).getVolumeGroupIds().remove(volumeGroup.getId().toString());
                            }
                        }
                        if (!NullColumnValueGetter.isNullURI(blockObject.getConsistencyGroup())) {
                            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, blockObject.getConsistencyGroup());
                            if (!cg.checkForType(BlockConsistencyGroup.Types.RP)) {
                                blockObject.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                            }
                        }
                        blockObject.setReplicationGroupInstance(NullColumnValueGetter.getNullStr());
                    }

                    dbClient.updateObject(blockObject);
                }
            }
            super.complete(dbClient, status, coded);

        } catch (Exception e) {
            log.error("Failed updating status. BlockConsistencyGroupRemoveVolume {}, for task "
                    + getOpId(), getId(), e);
        }
    }

}

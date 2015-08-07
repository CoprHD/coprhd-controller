/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import com.emc.storageos.services.OperationTypeEnum;

public class VplexMirrorDeactivateCompleter extends VplexMirrorTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(VplexMirrorDeactivateCompleter.class);
    public static final String MIRROR_DEACTIVATED_MSG = "Mirror %s deactivated for volume %s";
    public static final String MIRROR_DEACTIVATE_FAILED_MSG = "Failed to deactivate mirror %s for volume %s";

    public VplexMirrorDeactivateCompleter(URI mirror, String opId) {
        super(VplexMirror.class, mirror, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            super.complete(dbClient, status, coded);
            VplexMirror mirror = dbClient.queryObject(VplexMirror.class, getMirrorURI());
            Volume sourceVplexVolume = dbClient.queryObject(Volume.class, mirror.getSource());

            switch (status) {
                case error:
                    dbClient.error(VplexMirror.class, mirror.getId(), getOpId(), coded);
                    dbClient.error(Volume.class, sourceVplexVolume.getId(), getOpId(), coded);
                    break;
                default:
                    _log.info("Removing mirror {} from source volume {}", mirror.getId().toString(),
                            sourceVplexVolume.getId().toString());
                    sourceVplexVolume.getMirrors().remove(mirror.getId().toString());
                    dbClient.persistObject(sourceVplexVolume);
                    if (mirror.getAssociatedVolumes() != null && !mirror.getAssociatedVolumes().isEmpty()) {
                        for (String volumeUri : mirror.getAssociatedVolumes()) {
                            Volume assocVolume = dbClient.queryObject(Volume.class, URI.create(volumeUri));
                            if (assocVolume != null && !assocVolume.getInactive()) {
                                dbClient.markForDeletion(assocVolume);
                            }
                        }
                    }
                    dbClient.markForDeletion(mirror);
                    dbClient.ready(VplexMirror.class, mirror.getId(), getOpId());
                    dbClient.ready(Volume.class, sourceVplexVolume.getId(), getOpId());
            }

            recordVplexMirrorOperation(dbClient, OperationTypeEnum.DEACTIVATE_VOLUME_MIRROR,
                    status, eventMessage(status, sourceVplexVolume, mirror), mirror, sourceVplexVolume);
        } catch (Exception e) {
            _log.error("Failed updating status. VplexMirrorDeactivate {}, for task " + getOpId(), getId(), e);
        }
    }

    private String eventMessage(Operation.Status status, Volume volume, VplexMirror mirror) {
        return (Operation.Status.ready == status) ?
                String.format(MIRROR_DEACTIVATED_MSG, mirror.getLabel(), volume.getLabel()) :
                String.format(MIRROR_DEACTIVATE_FAILED_MSG, mirror.getLabel(), volume.getLabel());
    }
}

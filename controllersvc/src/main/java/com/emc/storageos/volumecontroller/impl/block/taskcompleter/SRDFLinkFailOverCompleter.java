/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class SRDFLinkFailOverCompleter extends SRDFTaskCompleter {

    private static final Logger _log = LoggerFactory.getLogger(SRDFLinkFailOverCompleter.class);
    private Volume.LinkStatus linkStatus = Volume.LinkStatus.FAILED_OVER;

    public SRDFLinkFailOverCompleter(List<URI> ids, String opId) {
        super(ids, opId);
    }

    public void setLinkStatus(Volume.LinkStatus status) {
        linkStatus = status;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            setDbClient(dbClient);
            recordSRDFOperation(dbClient, OperationTypeEnum.FAILOVER_SRDF_LINK, status, getSourceVolume().getId().toString(),
                    getTargetVolume().getId().toString());
        } catch (Exception e) {
            _log.error("Failed updating status. SRDFLinkFailOver {}, for task " + getOpId(), getId(), e);
        } finally {
            super.complete(dbClient, status, coded);

            // update remote replication pairs
            try {
                for (Volume v : getVolumes()) {
                    if (v.isVPlexVolume(dbClient)) {
                        // skip VPLEX volumes, as they delegate SRDF characteristics to their native volumes.
                        return;
                    }

                    if (v.getSrdfTargets() != null) {
                        List<URI> targetVolumeURIs = new ArrayList<>();
                        for (String targetId : v.getSrdfTargets()) {
                            targetVolumeURIs.add(URI.create(targetId));
                        }

                        List<Volume> targetVolumes = dbClient.queryObject(Volume.class, targetVolumeURIs);
                        for (Volume targetVolume : targetVolumes) {
                            // get RemoteReplicationPair object for this source and target volumes
                            // NOTE: SRDF volume roles may not correspond to volume rolls in remote replication pair
                            // due to the fact that SRDF swap operation changes source and target volumes, but
                            // in remote replication pair roles are immutable and swap changes replication direction
                            // property.
                            // call rr data client to check existence of rr pair for this pair of volumes.
                            // if not found log an error message and throw runtime exception
                            // if found, update replication state of the pair

                            targetVolume.setLinkStatus(getVolumeSRDFLinkStatusForSuccess().name());
                            //targetVolume.setAccessState(getVolumeAccessStateForSuccess(targetVolume).name());
                        }
                        dbClient.updateObject(targetVolumes);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected Volume.LinkStatus getVolumeSRDFLinkStatusForSuccess() {
        return linkStatus;
    }

}

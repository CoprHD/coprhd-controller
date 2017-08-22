/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

public class SRDFLinkSuspendCompleter extends SRDFTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(SRDFLinkSuspendCompleter.class);

    public SRDFLinkSuspendCompleter(List<URI> ids, String opId) {
        super(ids, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            setDbClient(dbClient);
            recordSRDFOperation(dbClient, OperationTypeEnum.SUSPEND_SRDF_LINK, status, getSourceVolume().getId().toString(),
                    getTargetVolume().getId().toString());
        } catch (Exception e) {
            _log.error("Failed updating status. SRDFMirrorSuspend {}, for task " + getOpId(), getId(), e);
        } finally {
            super.complete(dbClient, status, coded);
            // at this point we are done with all db updates for SRDF volumes, now update remote replication pairs
            super.updateRemoteReplicationPairs();
        }
    }

    @Override
    protected Volume.LinkStatus getVolumeSRDFLinkStatusForSuccess() {
        return Volume.LinkStatus.SUSPENDED;
    }
}

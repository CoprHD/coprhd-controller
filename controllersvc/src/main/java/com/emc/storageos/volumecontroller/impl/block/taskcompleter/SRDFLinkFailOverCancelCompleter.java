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
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class SRDFLinkFailOverCancelCompleter extends SRDFTaskCompleter {

    private static final Logger _log = LoggerFactory.getLogger(SRDFLinkFailOverCancelCompleter.class);

    public SRDFLinkFailOverCancelCompleter(List<URI> ids, String opId) {
        super(ids, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            setDbClient(dbClient);
            recordSRDFOperation(dbClient, OperationTypeEnum.FAILOVER_SRDF_LINK, status, getSourceVolume().getId().toString(),
                    getTargetVolume().getId().toString());
        } catch (Exception e) {
            _log.error("Failed updating status. SRDFLinkFailOverCancel {}, for task " + getOpId(), getId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    @Override
    protected Volume.LinkStatus getVolumeSRDFLinkStatusForSuccess() {
        return Volume.LinkStatus.IN_SYNC;
    }

}

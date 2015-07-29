/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.services.OperationTypeEnum;

@SuppressWarnings("serial")
public class VolumeCreateCompleter extends VolumeTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(VolumeCreateCompleter.class);

    public VolumeCreateCompleter(URI volUri, String task) {
        super(Volume.class, volUri, task);
        _log.info("Creating completer for OpId: " + getOpId());
    }

    public VolumeCreateCompleter(List<URI> volUris, String task) {
        super(Volume.class, volUris, task);
        _log.info("Creating completer for OpId: " + getOpId());
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            super.complete(dbClient, status, coded);
            for (URI id : getIds()) {
                switch (status) {
                    case error:
                        dbClient.error(Volume.class, id, getOpId(), coded);
                        break;
                    default:
                        dbClient.ready(Volume.class, id, getOpId());
                }

                _log.info(String.format("Done VolumeCreate - Id: %s, OpId: %s, status: %s",
                        id.toString(), getOpId(), status.name()));
                // TODO: this may be causing a double event. If so, break this completer out to a workflow version.
                recordBlockVolumeOperation(dbClient, OperationTypeEnum.CREATE_BLOCK_VOLUME, status, id.toString());
            }
        } catch (Exception e) {
            _log.error(String.format("Failed updating status for VolumeCreate - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        }
    }
}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

import java.net.URI;
import java.util.List;

public class SRDFRemoveDeviceGroupsCompleter extends SRDFTaskCompleter {
    public SRDFRemoveDeviceGroupsCompleter(List<URI> ids, String opId) {
        super(ids, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        super.complete(dbClient, status, coded);
    }
}

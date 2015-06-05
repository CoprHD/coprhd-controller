/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
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

import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

@SuppressWarnings("serial")
public class ExportMaskVolumeToStorageGroupCompleter extends ExportTaskCompleter {
    private static final org.slf4j.Logger _log = LoggerFactory.getLogger(ExportMaskVolumeToStorageGroupCompleter.class);

    public ExportMaskVolumeToStorageGroupCompleter(URI egUri, URI emUri, String task) {
        super(ExportGroup.class, egUri, emUri, task);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
    	_log.info("completed adding volumes to storage group");
    }

}

/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
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

/**
 * Completer called when a create full copy volumes workflow completes.
 */
public class CloneCreateWorkflowCompleter extends VolumeTaskCompleter {

    private static final long serialVersionUID = -8760349639300139009L;
    
    private static final Logger log = LoggerFactory
        .getLogger(CloneCreateWorkflowCompleter.class);

    public CloneCreateWorkflowCompleter(List<URI> fullCopyVolumeURIs, String task) {
        super(Volume.class, fullCopyVolumeURIs, task);
        setNotifyWorkflow(true);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
        throws DeviceControllerException {
        log.info("START FullCopyVolumeCreateWorkflowCompleter complete");
        super.setStatus(dbClient, status, coded);
        super.complete(dbClient, status, coded);
        log.info("END FullCopyVolumeCreateWorkflowCompleter complete");
    }
}
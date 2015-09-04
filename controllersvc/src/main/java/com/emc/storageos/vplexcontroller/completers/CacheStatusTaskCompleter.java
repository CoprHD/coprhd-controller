/*
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
package com.emc.storageos.vplexcontroller.completers;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Completer invoked when a cache invalidate job completes.
 */
public class CacheStatusTaskCompleter extends TaskCompleter {

    // For serialization.
    private static final long serialVersionUID = 1L;

    // Logger reference.
    private static final Logger s_logger = LoggerFactory
            .getLogger(CacheStatusTaskCompleter.class);

    /**
     *
     * @param migrationURI
     * @param taskId
     */
    public CacheStatusTaskCompleter(URI vplexVolumeURI, String opId) {
        super(Volume.class, vplexVolumeURI, opId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {

        URI vplexVolumeURI = getId();
        String opId = getOpId();
        s_logger.info(String.format(
                "Cache invalidate for volume %s for step %s completed with status %s",
                vplexVolumeURI, opId, status.name()));

        // Update the workflow status.
        updateWorkflowStatus(status, coded);
        s_logger.debug("Updated workflow status for VPLEX volume cache invalidate");
    }
}

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
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class SRDFAddPairToGroupCompleter extends SRDFTaskCompleter {
    private static final Logger log = LoggerFactory
            .getLogger(SRDFAddPairToGroupCompleter.class);
    private URI vpoolChangeURI;

    public SRDFAddPairToGroupCompleter(List<URI> volumeURIs,
            final URI vPoolChangeUri, String opId) {
        super(volumeURIs, opId);
        vpoolChangeURI = vPoolChangeUri;
    }
    
    public URI getVirtualPoolChangeURI() {
        return vpoolChangeURI;
    }

    @Override
    public void complete(final DbClient dbClient,
            final Operation.Status status, final ServiceCoded coded)
            throws DeviceControllerException {
        log.info("Completing with status: {}", status);
        setDbClient(dbClient);

        try {
            switch (status) {

                case ready:
                    for (Volume source : getVolumes()) {
                        if (null != vpoolChangeURI) {
                            source.setVirtualPool(vpoolChangeURI);
                            dbClient.persistObject(source);
                            log.info("Updating virtual Pool {} to sourceVlume {}",
                                    vpoolChangeURI, source.getNativeGuid());
                        }
                    }

                default:
                    log.info("Unable to handle status: {}", status);
            }

        } catch (Exception e) {
            log.info("Failed to update status for task {}", getOpId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    @Override
    protected Volume.LinkStatus getVolumeSRDFLinkStatusForSuccess() {
        return Volume.LinkStatus.IN_SYNC;
    }
}

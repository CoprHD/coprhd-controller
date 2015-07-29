/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation
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
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class CloneTaskCompleter extends VolumeTaskCompleter {

    private static final long serialVersionUID = -5808737150117378443L;
    private static final Logger log = LoggerFactory.getLogger(CloneTaskCompleter.class);

    public CloneTaskCompleter(List<URI> fullCopyVolumeURIs, String task) {
        super(Volume.class, fullCopyVolumeURIs, task);
        setNotifyWorkflow(true);
    }

    public CloneTaskCompleter(URI fullCopyVolumeURI, String task) {
        super(Volume.class, fullCopyVolumeURI, task);
        setNotifyWorkflow(true);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        log.info("START FullCopyVolumeTaskCompleter complete");
        super.complete(dbClient, status, coded);

        try {
            List<Volume> fullCopyvolumes = dbClient.queryObject(Volume.class, getIds());

            if (fullCopyvolumes != null && !fullCopyvolumes.isEmpty()) {
                Volume fullCopyVolume = fullCopyvolumes.get(0);

                Class<? extends BlockObject> sourceClass = Volume.class;
                if (URIUtil.isType(fullCopyVolume.getAssociatedSourceVolume(), BlockSnapshot.class)) {
                    sourceClass = BlockSnapshot.class;
                }

                switch (status) {
                    case error:
                        dbClient.error(sourceClass, fullCopyVolume.getId(), getOpId(), coded);
                        break;
                    default:
                        dbClient.ready(sourceClass, fullCopyVolume.getId(), getOpId());
                }
            }
        } catch (Exception e) {
            log.error("Failed to update status for volume clone", e);
        }
    }
}

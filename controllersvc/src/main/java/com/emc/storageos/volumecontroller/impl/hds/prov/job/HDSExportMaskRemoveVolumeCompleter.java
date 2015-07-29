/*
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

package com.emc.storageos.volumecontroller.impl.hds.prov.job;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;

public class HDSExportMaskRemoveVolumeCompleter extends ExportTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(HDSExportMaskRemoveVolumeCompleter.class);

    private Collection<URI> _volumes;

    public HDSExportMaskRemoveVolumeCompleter(URI egUri, URI emUri, Collection<URI> volumes,
            String task) {
        super(ExportGroup.class, egUri, emUri, task);
        _volumes = new ArrayList<URI>();
        _volumes.addAll(volumes);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            ExportMask exportMask = (getMask() != null) ?
                    dbClient.queryObject(ExportMask.class, getMask()) : null;
            for (URI volumeURI : _volumes) {
                BlockObject volume = BlockObject.fetch(dbClient, volumeURI);
                if (exportMask != null && status == Operation.Status.ready) {
                    exportMask.removeFromUserCreatedVolumes(volume);
                    exportMask.removeVolume(volume.getId());
                }
            }
            _log.info(String.format(
                    "Done ExportMaskRemoveVolume - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));
        } catch (Exception e) {
            _log.error(String.format(
                    "Failed updating status for ExportMaskRemoveVolume - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

}

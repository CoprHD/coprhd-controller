/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.util.ExportUtils;

@SuppressWarnings("serial")
public class ExportMaskAddVolumeCompleter extends ExportTaskCompleter {
    private static final org.slf4j.Logger _log = LoggerFactory.getLogger(ExportMaskAddVolumeCompleter.class);
    private List<URI> _volumes;
    private Map<URI, Integer> _volumeMap;

    public ExportMaskAddVolumeCompleter(URI egUri, URI emUri, Map<URI, Integer> volumes,
            String task) {
        super(ExportGroup.class, egUri, emUri, task);
        _volumes = new ArrayList<URI>();
        _volumes.addAll(volumes.keySet());
        _volumeMap = new HashMap<URI, Integer>();
        _volumeMap.putAll(volumes);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            ExportMask exportMask = (getMask() != null) ?
                    dbClient.queryObject(ExportMask.class, getMask()) : null;
            for (URI volumeURI : _volumes) {
                BlockObject volume = BlockObject.fetch(dbClient, volumeURI);
                _log.info(String.format("Done ExportMaskAddVolume - Id: %s, OpId: %s, status: %s",
                        getId().toString(), getOpId(), status.name()));

                if (exportMask != null && status == Operation.Status.ready) {
                    exportMask.addToUserCreatedVolumes(volume);
                }
            }

            if (exportMask != null && status == Operation.Status.ready) {
                exportMask.addVolumes(_volumeMap);
                exportGroup.addExportMask(exportMask.getId());
                ExportUtils.reconcileHLUs(dbClient, exportGroup, exportMask, _volumeMap);
                dbClient.updateObject(exportMask);
                dbClient.updateObject(exportGroup);
            }

        } catch (Exception e) {
            _log.error(String.format("Failed updating status for ExportMaskAddVolume - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

}

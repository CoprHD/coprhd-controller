/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class ExportMaskChangePortGroupAddMaskCompleter extends ExportTaskCompleter{
    private static final Logger _log = LoggerFactory.getLogger(ExportMaskChangePortGroupAddMaskCompleter.class);
    
    public ExportMaskChangePortGroupAddMaskCompleter(URI exportMaskURI, URI exportGroupURI, String task) {
        super(ExportGroup.class, exportGroupURI, exportMaskURI, task);
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status,
            ServiceCoded coded) throws DeviceControllerException {
        try {
            List<ExportGroup> egs = dbClient.queryObject(ExportGroup.class, getExportGroups() );
            
            ExportMask exportMask = (getMask() != null) ?
                    dbClient.queryObject(ExportMask.class, getMask()) : null;
            if (exportMask != null && status == Operation.Status.ready) {
                for (ExportGroup eg : egs) {
                    eg.addExportMask(exportMask.getId());
                    _log.info(String.format("Updated the export group %s", eg.getLabel()));
                }
                dbClient.updateObject(egs);
                updatePortGroupVolumeCount(exportMask.getPortGroup(), dbClient);
            }
        } catch (Exception e) {
            _log.error(String.format(
                    "Failed updating status for change port group add mask - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }
}

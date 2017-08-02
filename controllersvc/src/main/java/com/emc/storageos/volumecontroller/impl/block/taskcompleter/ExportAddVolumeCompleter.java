/*
 * Copyright (c) 2008-2011 EMC Corporation
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
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

@SuppressWarnings("serial")
public class ExportAddVolumeCompleter extends ExportTaskCompleter {
    private static final org.slf4j.Logger _log = LoggerFactory.getLogger(ExportAddVolumeCompleter.class);
    private List<URI> _volumes;
    private Map<URI, Integer> _volumeMap;
    private static final String EXPORT_ADD_VOLUME_MSG = "Volume %s added to ExportGroup %s";
    private static final String EXPORT_ADD_VOLUME_MSG_FAILED_MSG = "Failed to add volume %s to ExportGroup %s";

    public ExportAddVolumeCompleter(URI egUri, Map<URI, Integer> volumes,
            String task) {
        super(ExportGroup.class, egUri, task);
        _volumes = new ArrayList<URI>();
        _volumes.addAll(volumes.keySet());
        _volumeMap = new HashMap<URI, Integer>();
        _volumeMap.putAll(volumes);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            _log.info("ExportAddVolumeCompleter START");
            _log.info(String.format("Done ExportMaskAddVolume - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));

            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            for (URI volumeURI : _volumes) {
                BlockObject volume = BlockObject.fetch(dbClient, volumeURI);

                recordBlockExportOperation(dbClient, OperationTypeEnum.ADD_EXPORT_VOLUME, status,
                        eventMessage(status, volume, exportGroup), exportGroup, volume);

                if (status.name().equals(Operation.Status.error.name())) {
                    exportGroup.removeVolume(volumeURI);
                }
            }

            Operation operation = new Operation();
            switch (status) {
                case error:
                    operation.error(coded);
                    break;
                case ready:
                    operation.ready();
                    break;
                case suspended_no_error:
                    operation.suspendedNoError();
                    break;
                case suspended_error:
                    operation.suspendedError(coded);
                    break;
                default:
                    break;
            }
            exportGroup.getOpStatus().updateTaskStatus(getOpId(), operation);
            dbClient.updateObject(exportGroup);
        } catch (Exception e) {
            _log.error(String.format("Failed updating status for ExportMaskAddVolume - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
        _log.info("ExportAddVolumeCompleter END");

    }

    private String eventMessage(Operation.Status status, BlockObject volume, ExportGroup exportGroup) {
        return (status == Operation.Status.ready) ?
                String.format(EXPORT_ADD_VOLUME_MSG, volume.getLabel(), exportGroup.getLabel()) :
                String.format(EXPORT_ADD_VOLUME_MSG_FAILED_MSG, volume.getLabel(), exportGroup.getLabel());
    }
}

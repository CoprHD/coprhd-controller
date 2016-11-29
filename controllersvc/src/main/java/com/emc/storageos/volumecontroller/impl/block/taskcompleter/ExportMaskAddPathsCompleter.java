/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class ExportMaskAddPathsCompleter extends ExportTaskCompleter{
    private static final org.slf4j.Logger _log = LoggerFactory.getLogger(ExportMaskAddPathsCompleter.class);
    private Map<URI, List<URI>> newPaths;
    
    public ExportMaskAddPathsCompleter(URI id, URI emURI, String opId, Map<URI, List<URI>> newPaths) {
        super(ExportGroup.class, id, emURI, opId);
        this.newPaths = newPaths;
    }

    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status,
            ServiceCoded coded) throws DeviceControllerException {
        try {
            ExportMask exportMask = dbClient.queryObject(ExportMask.class, getMask());
            // update zoning map
            StringSetMap zoningMap = exportMask.getZoningMap();
            if (zoningMap != null && !zoningMap.isEmpty()) {
                for (Map.Entry<URI, List<URI>> entry : newPaths.entrySet()) {
                    URI initiator = entry.getKey();
                    List<URI> ports = entry.getValue();
                    StringSet existingPorts = zoningMap.get(initiator.toString());
                    Set<String> newPorts = StringSetUtil.uriListToSet(ports);
                    if (existingPorts != null) {
                        existingPorts.addAll(newPorts);
                    } else {
                        existingPorts = new StringSet(newPorts);
                    }
                    zoningMap.put(initiator.toString(), existingPorts);
                }
                dbClient.updateObject(exportMask);
            } else {
                _log.warn(String.format("No zoning map existing in the export mask %s", getMask().toString()));
            }
        } catch (Exception e) {
            _log.error(String.format(
                    "Failed updating status for ExportMaskAddPaths - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }
}

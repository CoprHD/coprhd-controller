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

public class ZoningAddPathsCompleter extends ExportTaskCompleter{

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ZoningAddPathsCompleter.class);
    private Map<URI, Map<URI, List<URI>>> exportMaskAdjustedPathMap;
    
    public ZoningAddPathsCompleter(URI id, String opId, Map<URI, Map<URI, List<URI>>> newPaths) {
        super(ExportGroup.class, id, opId);
        this.exportMaskAdjustedPathMap = newPaths;
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status,
            ServiceCoded coded) throws DeviceControllerException {
        try {
            if (status == Operation.Status.ready && !exportMaskAdjustedPathMap.isEmpty()) {
                log.info("Updating export mask zoning map in DB");
                for (Map.Entry<URI, Map<URI, List<URI>>> maskPathEntry : exportMaskAdjustedPathMap.entrySet()) {
                    URI maskURI = maskPathEntry.getKey();
                    Map<URI, List<URI>> newPaths = maskPathEntry.getValue();
                    ExportMask exportMask = dbClient.queryObject(ExportMask.class, maskURI);
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
                        
                    } else {
                        for (Map.Entry<URI, List<URI>> entry : newPaths.entrySet()) {
                            zoningMap.put(entry.getKey().toString(), StringSetUtil.uriListToStringSet(entry.getValue()));
                        }
                    }
                    dbClient.updateObject(exportMask);
                }
            }
        } catch (Exception e) {
            log.error(String.format(
                    "Failed updating status for ExportMaskAddPaths - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }
}

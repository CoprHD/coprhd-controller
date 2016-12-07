package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class ZoningRemovePathsCompleter extends ExportTaskCompleter{

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ZoningRemovePathsCompleter.class);
    private Map<URI, Map<URI, List<URI>>> exportMaskAdjustedPathMap;
    
    public ZoningRemovePathsCompleter(URI id, String opId, Map<URI, Map<URI, List<URI>>> adjustedPaths) {
        super(ExportGroup.class, id, opId);
        this.exportMaskAdjustedPathMap = adjustedPaths;
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status,
            ServiceCoded coded) throws DeviceControllerException {
        try {
            if (status == Operation.Status.ready && !exportMaskAdjustedPathMap.isEmpty()) {
                
                for (Map.Entry<URI, Map<URI, List<URI>>> maskPathEntry : exportMaskAdjustedPathMap.entrySet()) {
                    URI maskURI = maskPathEntry.getKey();
                    Map<URI, List<URI>> zoningPaths = maskPathEntry.getValue();
                    ExportMask exportMask = dbClient.queryObject(ExportMask.class, maskURI);
                    // update zoning map
                    StringSetMap zoningMap = exportMask.getZoningMap();
                    zoningMap.clear();
                    for (Map.Entry<URI, List<URI>> zoningPath : zoningPaths.entrySet()) {
                        zoningMap.put(zoningPath.getKey().toString(), StringSetUtil.uriListToStringSet(zoningPath.getValue()));
                    }
                    dbClient.updateObject(exportMask);
                }
            }
        } catch (Exception e) {
            log.error(String.format(
                    "Failed updating status for ZoningRemovePaths - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

}

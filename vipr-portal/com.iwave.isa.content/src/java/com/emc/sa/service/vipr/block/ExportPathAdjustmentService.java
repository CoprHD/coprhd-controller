/*
 * Copyright (c) 2017  DELL EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.EXPORT;
import static com.emc.sa.service.ServiceParams.MIN_PATHS;
import static com.emc.sa.service.ServiceParams.MAX_PATHS;
import static com.emc.sa.service.ServiceParams.PATHS_PER_INITIATOR;
import static com.emc.sa.service.ServiceParams.PORTS;
import static com.emc.sa.service.ServiceParams.STORAGE_SYSTEM;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.RESULTING_PATHS;
import static com.emc.sa.service.ServiceParams.REMOVED_PATHS;
import static com.emc.sa.service.ServiceParams.USE_EXISTING_PATHS;
import static com.emc.sa.service.ServiceParams.SUSPEND_WAIT;


import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.sa.asset.providers.BlockProvider;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.ExportPathsPreview;
import com.emc.sa.util.CatalogSerializationUtils;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.export.ExportPathsAdjustmentPreviewRestRep;
import com.emc.storageos.model.block.export.InitiatorPathParam;
import com.emc.storageos.model.block.export.InitiatorPortMapRestRep;

@Service("ExportPathAdjustment")
public class ExportPathAdjustmentService extends ViPRService {

    @Param(HOST)
    protected URI host;
    
    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;
    
    @Param(EXPORT)
    protected URI exportId;
    
    @Param(MIN_PATHS)
    protected Integer minPaths;
    
    @Param(MAX_PATHS)
    protected Integer maxPaths;
    
    @Param(PATHS_PER_INITIATOR)
    protected Integer pathsPerInitiator;
    
    @Param(USE_EXISTING_PATHS)
    protected String useExistingPaths;
    
    @Param(STORAGE_SYSTEM)
    protected URI storageSystem;
    
    @Param(value = PORTS, required = false)
    protected List<URI> ports;
    
    @Param(SUSPEND_WAIT)
    protected boolean suspendWait;
    
    @Param(value = RESULTING_PATHS, required = false)
    protected List<String> resultingPaths;
    
    @Param(value = REMOVED_PATHS, required = false)
    protected List<String> removedPaths;
    
    private Map<URI, List<URI>> resultingPathsMap = new HashMap<URI, List<URI>>();
    private Map<URI, List<URI>> removedPathsMap = new HashMap<URI, List<URI>>();
    
    @Override
    public void precheck() throws Exception {
        if ((resultingPaths == null || resultingPaths.isEmpty()) &&
                (removedPaths == null || removedPaths.isEmpty())) {
            // COP-34388 ViPR skips preview page and executes adjust export path service
            // even when preview has not been generated. This can have serious consequences
            // and hence we need to avoid this.  
            ExecutionUtils.fail("failTask.AdjustExportPaths.precheck.previewNotGenerated",args());
        } else {
            try {
                if (resultingPaths != null) {
                    for (String affected : resultingPaths) {
                        Map<URI, List<URI>> port = (Map<URI, List<URI>>) CatalogSerializationUtils.serializeFromString(affected);
                        resultingPathsMap.putAll(port);
                    }
                }
                if (removedPaths != null) {
                    for (String removed : removedPaths) {
                        Map<URI, List<URI>> port = (Map<URI, List<URI>>) CatalogSerializationUtils.serializeFromString(removed);
                        removedPathsMap.putAll(port);
                   }
                }
            } catch (Exception ex) {
                ExecutionUtils.fail("failTask.AdjustExportPaths.deserialize", args(), ex.getMessage());
            }
        }
    }
    
    @Override
    public void execute() throws Exception {
        // build affect paths
        List<InitiatorPathParam> toSendAffectedPaths = new ArrayList<InitiatorPathParam>();
        for (Map.Entry<URI, List<URI>> entry : resultingPathsMap.entrySet()) {
            InitiatorPathParam pathParam = new InitiatorPathParam();
            
            pathParam.setInitiator(entry.getKey());
            pathParam.setStoragePorts(entry.getValue());
            
            toSendAffectedPaths.add(pathParam);
        }
       
        // build removed paths
        List<InitiatorPathParam> toSendRemovedPaths = new ArrayList<InitiatorPathParam>();
        for (Map.Entry<URI, List<URI>> entry : removedPathsMap.entrySet()) {
            InitiatorPathParam pathParam = new InitiatorPathParam();
            
            pathParam.setInitiator(entry.getKey());
            pathParam.setStoragePorts(entry.getValue());
            
            toSendRemovedPaths.add(pathParam);
        }
        
        BlockStorageUtils.adjustExportPaths(virtualArray, minPaths, maxPaths, pathsPerInitiator, storageSystem, exportId,
                toSendAffectedPaths, toSendRemovedPaths, suspendWait);
    }
    
    private void runExportPathsPreview() {
        Boolean useExisting = (BlockProvider.YES_VALUE.equalsIgnoreCase(useExistingPaths) ? true : false);
        ExportPathsAdjustmentPreviewRestRep previewRestRep = execute(new ExportPathsPreview(host, virtualArray, exportId,
              minPaths, maxPaths, pathsPerInitiator, storageSystem, ports, useExisting));
        List<InitiatorPortMapRestRep> affectedPaths = previewRestRep.getAdjustedPaths();
        List<InitiatorPortMapRestRep> removedPaths = previewRestRep.getRemovedPaths();
          
        // build the affected path 
        for (InitiatorPortMapRestRep ipm : affectedPaths) {
            List<URI> portList = new ArrayList<URI>();
            for (NamedRelatedResourceRep port : ipm.getStoragePorts()) {
                portList.add(port.getId());
            }
            resultingPathsMap.put(ipm.getInitiator().getId(), portList);
        }
        
        // build the removed path 
        for (InitiatorPortMapRestRep ipm : removedPaths) {
            List<URI> portList = new ArrayList<URI>();
            for (NamedRelatedResourceRep port : ipm.getStoragePorts()) {
                portList.add(port.getId());
            }
            removedPathsMap.put(ipm.getInitiator().getId(), portList);
        }
    }
}

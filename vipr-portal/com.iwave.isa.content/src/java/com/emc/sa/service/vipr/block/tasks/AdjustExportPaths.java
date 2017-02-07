/*
 * Copyright (c) 2017  DELL EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.block.export.ExportPathsAdjustmentParam;
import com.emc.storageos.model.block.export.InitiatorPathParam;
import com.emc.vipr.client.Task;

public class AdjustExportPaths extends WaitForTask<ExportGroupRestRep> {

    private URI virtualArray;
    private Integer minPaths;
    private Integer maxPaths;
    private Integer pathsPerInitiator;
    private URI storageSystemId;
    private boolean suspendWait;
    
    private List<InitiatorPathParam> addedPaths;
    private List<InitiatorPathParam> removedPaths;
    
    private URI exportId;
    
    public AdjustExportPaths(URI virtualArray, Integer minPaths, Integer maxPaths, Integer pathsPerInitiator, URI storageSystemId,
            URI exportId, List<InitiatorPathParam> addedPaths, List<InitiatorPathParam> removedPaths, boolean suspendWait) {
        this.virtualArray = virtualArray;
        this.minPaths = minPaths;
        this.maxPaths = maxPaths;
        this.pathsPerInitiator = pathsPerInitiator;
        this.storageSystemId = storageSystemId;
        this.suspendWait = suspendWait;
        
        this.addedPaths = addedPaths;
        this.removedPaths = removedPaths;
        
        this.exportId = exportId;
        
        provideDetailArgs(this.virtualArray, this.exportId, this.minPaths, this.maxPaths, this.pathsPerInitiator,
                buildPathDetails(this.addedPaths), buildPathDetails(this.removedPaths));
    }
    
    @Override
    protected Task<ExportGroupRestRep> doExecute() throws Exception {
        ExportPathsAdjustmentParam param = new ExportPathsAdjustmentParam();
        ExportPathParameters exportPathParameters = new ExportPathParameters();
        
        exportPathParameters.setMinPaths(minPaths);
        exportPathParameters.setMaxPaths(maxPaths);
        exportPathParameters.setPathsPerInitiator(pathsPerInitiator);
        
        param.setExportPathParameters(exportPathParameters);
        param.setVirtualArray(virtualArray);
        param.setStorageSystem(storageSystemId);

        param.setAdjustedPaths(addedPaths);
        param.setRemovedPaths(removedPaths);

        param.setWaitBeforeRemovePaths(suspendWait);

        return getClient().blockExports().pathAdjustment(exportId, param);
    }
    
    private String buildPathDetails(List<InitiatorPathParam> param) {
        StringBuilder builder = new StringBuilder();
        
        for (InitiatorPathParam ini : param) {
            builder.append(" " + ini.getInitiator().toString() + ": [");
            List<String> ports = new ArrayList<String>();
            for (URI uri : ini.getStoragePorts()) {
                ports.add(uri.toString());
            }
            builder.append(String.join(", ", ports));
            builder.append("],");
        }
        
        return builder.toString();
    }
}

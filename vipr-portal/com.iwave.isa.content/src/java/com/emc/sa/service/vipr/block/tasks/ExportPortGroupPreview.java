/*
 * Copyright (c) 2017  DELL EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.block.export.ExportPathsAdjustmentPreviewParam;
import com.emc.storageos.model.block.export.ExportPathsAdjustmentPreviewRestRep;

public class ExportPortGroupPreview extends ViPRExecutionTask<ExportPathsAdjustmentPreviewRestRep> {
    // BH Remove class?
    
    private URI hostOrClusterId;
    private URI virtualArray;
    private Integer minPaths;
    private Integer maxPaths;
    private Integer pathsPerInitiator;
    private URI storageSystemId;
    private List<URI> portGroups;
    
    private URI exportId;
    
    public ExportPortGroupPreview(URI hostOrClusterId, URI virtualArray, URI exportId, Integer minPaths, Integer maxPaths, 
            Integer pathsPerInitiator, URI storageSystemId, List<URI> portGroups) {
        
        this.hostOrClusterId = hostOrClusterId;
        this.virtualArray = virtualArray;
        this.minPaths = minPaths;
        this.maxPaths = maxPaths;
        this.pathsPerInitiator = pathsPerInitiator;
        this.storageSystemId = storageSystemId;
        this.portGroups = portGroups;
        
        this.exportId = exportId;
        
        provideDetailArgs(this.exportId, this.hostOrClusterId, this.virtualArray, this.minPaths, this.maxPaths, 
                this.pathsPerInitiator, this.storageSystemId, portGroups != null ? this.portGroups.toString() : "N/A");
    }
    
    @Override
    public ExportPathsAdjustmentPreviewRestRep executeTask() throws Exception {
        ExportPathsAdjustmentPreviewParam param = new ExportPathsAdjustmentPreviewParam();
        ExportPathParameters exportPathParameters = new ExportPathParameters();
        
        exportPathParameters.setMinPaths(minPaths);
        exportPathParameters.setMaxPaths(maxPaths);
        exportPathParameters.setPathsPerInitiator(pathsPerInitiator);
        
        if (portGroups != null) {
            exportPathParameters.setStoragePorts(portGroups);
        }
 
        param.setStorageSystem(storageSystemId);
        param.setVirtualArray(virtualArray);
        
        param.setExportPathParameters(exportPathParameters);
        
        return getClient().blockExports().getExportPortGroupAdjustmentPreview(exportId, param);
    }
}

package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.block.export.ExportPathsAdjustmentPreviewParam;
import com.emc.storageos.model.block.export.ExportPathsAdjustmentPreviewRestRep;

public class ExportPathsPreview extends ViPRExecutionTask<ExportPathsAdjustmentPreviewRestRep> {
    
    private URI hostOrClusterId;
    private URI virtualArray;
    private Integer minPaths;
    private Integer maxPaths;
    private Integer pathsPerInitiator;
    private URI storageSystemId;
    private List<URI> ports;
    
    private URI exportId;
    
    public ExportPathsPreview(URI hostOrClusterId, URI virtualArray, Integer minPaths, Integer maxPaths, Integer pathsPerInitiator,
            URI storageSystemId, List<URI> ports, URI exportId) {
        
        this.hostOrClusterId = hostOrClusterId;
        this.virtualArray = virtualArray;
        this.minPaths = minPaths;
        this.maxPaths = maxPaths;
        this.pathsPerInitiator = pathsPerInitiator;
        this.storageSystemId = storageSystemId;
        this.ports = ports;
        
        this.exportId = exportId;
     // TODO: addDetails     provideDetailArgs(name, getMessage("CreateExport.cluster"), hostName, volumeIds, hlu);
    }
    
    @Override
    public ExportPathsAdjustmentPreviewRestRep executeTask() throws Exception {
        ExportPathsAdjustmentPreviewParam param = new ExportPathsAdjustmentPreviewParam();
        ExportPathParameters exportPathParameters = new ExportPathParameters();
        
        exportPathParameters.setMinPaths(minPaths);
        exportPathParameters.setMaxPaths(maxPaths);
        exportPathParameters.setPathsPerInitiator(pathsPerInitiator);
        
        exportPathParameters.setStoragePorts(ports);
        
//        List<ExportGroupRestRep> exports = getClient().blockExports().findByHost(hostOrClusterId, null, null);
//        URI exportId = null;
//        
//        if (exports.isEmpty()) {
//            error("No Export found for host : %s", hostOrClusterId);
//        } else {
//            exportId = exports.get(0).getId();
//        }
        
        
        param.setStorageSystem(storageSystemId);
        param.setVirtualArray(virtualArray);
        
        param.setExportPathParameters(exportPathParameters);
        
        return getClient().blockExports().getExportPathAdjustmentPreview(exportId, param);
    }
}

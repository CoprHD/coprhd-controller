package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.MIN_PATHS;
import static com.emc.sa.service.ServiceParams.MAX_PATHS;
import static com.emc.sa.service.ServiceParams.PATHS_PER_INITIATOR;
import static com.emc.sa.service.ServiceParams.PORTS;
import static com.emc.sa.service.ServiceParams.STORAGE_SYSTEM;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.AFFECTED_PORTS;
import static com.emc.sa.service.ServiceParams.REMOVED_PORTS;


import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.InitiatorPathParam;

@Service("ExportPathAdjustment")
public class ExportPathAdjustmentService extends ViPRService {

    @Param(HOST)
    protected URI host;
    
    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;
    
    @Param(MIN_PATHS)
    protected Integer minPaths;
    
    @Param(MAX_PATHS)
    protected Integer maxPaths;
    
    @Param(PATHS_PER_INITIATOR)
    protected Integer pathsPerInitiator;
    
    @Param(STORAGE_SYSTEM)
    protected URI storageSystem;
    
    @Param(PORTS)
    protected List<URI> ports;
    
    @Param(value = AFFECTED_PORTS, required = false)
    protected List<String> affectedPorts;
    
    @Param(value = REMOVED_PORTS, required = false)
    protected List<String> removedPorts;
    
    private Map<URI, List<URI>> affectedPortsMap = new HashMap<URI, List<URI>>();
    private Map<URI, List<URI>> removedPortsMap = new HashMap<URI, List<URI>>();
    
    @Override
    public void precheck() throws Exception {
        try {
            for (String affected : affectedPorts) {
                Map<URI, List<URI>> port = (Map<URI, List<URI>>) BlockStorageUtils.serializeFromString(affected);
                affectedPortsMap.putAll(port);
            }
            
            for (String removed : removedPorts) {
                Map<URI, List<URI>> port = (Map<URI, List<URI>>) BlockStorageUtils.serializeFromString(removed);
                removedPortsMap.putAll(port);
            }
//            removedPortsMap = (Map<URI, List<URI>>) BlockStorageUtils.serializeFromString(removedPorts);
        } catch (Exception ex) {
            // TODO: fail order, log error
        }
    }
    
    @Override
    public void execute() throws Exception {
        List<ExportGroupRestRep> exports = getClient().blockExports().findByHost(host, null, null);
        URI exportId = null;
        
        if (exports.isEmpty()) {
            error("No Export found for host : %s", host);
        } else {
            exportId = exports.get(0).getId();
        }

//        ExportPathsAdjustmentPreviewRestRep previewRestRep = execute(new ExportPathsPreview(host, virtualArray, minPaths, maxPaths, 
//                pathsPerInitiator, storageSystem, ports, exportId));
//        List<InitiatorPortMapRestRep> affectedPaths = previewRestRep.getAddedPaths();
//        List<InitiatorPortMapRestRep> removedPaths = previewRestRep.getRemovedPaths();
//        
        // build affect paths
        List<InitiatorPathParam> toSendAffectedPaths = new ArrayList<InitiatorPathParam>();
        for (Map.Entry<URI, List<URI>> entry : affectedPortsMap.entrySet()) {
            InitiatorPathParam pathParam = new InitiatorPathParam();
            
            pathParam.setInitiator(entry.getKey());
            pathParam.setStoragePorts(entry.getValue());
            
            toSendAffectedPaths.add(pathParam);
        }
       
        // build removed paths
        List<InitiatorPathParam> toSendRemovedPaths = new ArrayList<InitiatorPathParam>();
        for (Map.Entry<URI, List<URI>> entry : removedPortsMap.entrySet()) {
            InitiatorPathParam pathParam = new InitiatorPathParam();
            
            pathParam.setInitiator(entry.getKey());
            pathParam.setStoragePorts(entry.getValue());
            
            toSendRemovedPaths.add(pathParam);
        }
        
        BlockStorageUtils.adjustExportPaths(minPaths, maxPaths, pathsPerInitiator, storageSystem, exportId, 
                toSendAffectedPaths, toSendRemovedPaths);
        //execute(new AdjustExportPaths(minPaths, maxPaths, pathsPerInitiator, storageSystem, exportId, 
        //        toSendAffectedPaths, toSendRemovedPaths));

    }
}

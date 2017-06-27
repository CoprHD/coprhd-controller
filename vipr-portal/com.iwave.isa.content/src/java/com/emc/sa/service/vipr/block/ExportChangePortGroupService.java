/*
 * Copyright (c) 2017  DELL EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.EXPORT;
import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.CHANGE_PORT_GROUP;
import static com.emc.sa.service.ServiceParams.STORAGE_SYSTEM;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("ExportChangePortGroup")
public class ExportChangePortGroupService extends ViPRService {

    @Param(HOST)
    protected URI host;
    
    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;
    
    @Param(EXPORT)
    protected URI exportId;
    
    @Param(STORAGE_SYSTEM)
    protected URI storageSystem;
    
    @Param(value = CHANGE_PORT_GROUP, required = true)
    protected URI newPortGroup;
    
    @Override
    public void precheck() throws Exception {
//        if ((resultingPaths == null || resultingPaths.isEmpty()) &&
//                (removedPaths == null || removedPaths.isEmpty())) {
//            // if we have no affected or removed, the preview as likely not been generated, so we're going to 
//            // generate it before running the service. This is to support the api call and allowing the user
//            // to omit sending the serialize string. 
//            runExportPortGroupPreview();
//        } else {
//            try {
//                if (resultingPaths != null) {
//                    for (String affected : resultingPaths) {
//                        Map<URI, List<URI>> port = (Map<URI, List<URI>>) CatalogSerializationUtils.serializeFromString(affected);
//                        resultingPathsMap.putAll(port);
//                    }
//                }
//                if (removedPaths != null) {
//                    for (String removed : removedPaths) {
//                        Map<URI, List<URI>> port = (Map<URI, List<URI>>) CatalogSerializationUtils.serializeFromString(removed);
//                        removedPathsMap.putAll(port);
//                   }
//                }
//            } catch (Exception ex) {
//                ExecutionUtils.fail("failTask.AdjustExportPaths.deserialize", args(), ex.getMessage());
//            }
//        }
    }
    
    @Override
    public void execute() throws Exception {
//        // build affect paths
//        List<InitiatorPathParam> toSendAffectedPaths = new ArrayList<InitiatorPathParam>();
//        for (Map.Entry<URI, List<URI>> entry : resultingPathsMap.entrySet()) {
//            InitiatorPathParam pathParam = new InitiatorPathParam();
//            
//            pathParam.setInitiator(entry.getKey());
//            pathParam.setStoragePorts(entry.getValue());
//            
//            toSendAffectedPaths.add(pathParam);
//        }
//       
//        // build removed paths
//        List<InitiatorPathParam> toSendRemovedPaths = new ArrayList<InitiatorPathParam>();
//        for (Map.Entry<URI, List<URI>> entry : removedPathsMap.entrySet()) {
//            InitiatorPathParam pathParam = new InitiatorPathParam();
//            
//            pathParam.setInitiator(entry.getKey());
//            pathParam.setStoragePorts(entry.getValue());
//            
//            toSendRemovedPaths.add(pathParam);
//        }
//        
//        BlockStorageUtils.adjustExportPaths(virtualArray, minPaths, maxPaths, pathsPerInitiator, storageSystem, exportId,
//                toSendAffectedPaths, toSendRemovedPaths, suspendWait);
    }
    
    private void runExportPortGroupPreview() {
//        ExportPathsAdjustmentPreviewRestRep previewRestRep = execute(new ExportPortGroupPreview(host, virtualArray, exportId,
//              minPaths, maxPaths, pathsPerInitiator, storageSystem, portGroups));
//        List<InitiatorPortMapRestRep> affectedPaths = previewRestRep.getAdjustedPaths();
//        List<InitiatorPortMapRestRep> removedPaths = previewRestRep.getRemovedPaths();
//          
//        // build the affected path 
//        for (InitiatorPortMapRestRep ipm : affectedPaths) {
//            List<URI> portList = new ArrayList<URI>();
//            for (NamedRelatedResourceRep port : ipm.getStoragePorts()) {
//                portList.add(port.getId());
//            }
//            resultingPathsMap.put(ipm.getInitiator().getId(), portList);
//        }
//        
//        // build the removed path 
//        for (InitiatorPortMapRestRep ipm : removedPaths) {
//            List<URI> portList = new ArrayList<URI>();
//            for (NamedRelatedResourceRep port : ipm.getStoragePorts()) {
//                portList.add(port.getId());
//            }
//            removedPathsMap.put(ipm.getInitiator().getId(), portList);
//        }
    }
}

/*
 * Copyright (c) 2017  DELL EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.EXPORT;
import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.CHANGE_PORT_GROUP;
import static com.emc.sa.service.ServiceParams.CURRENT_PORT_GROUP;
import static com.emc.sa.service.ServiceParams.STORAGE_SYSTEM;
import static com.emc.sa.service.ServiceParams.SUSPEND_WAIT;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.execute;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.ExportChangePortGroup;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.vipr.client.Task;

@Service("ExportChangePortGroup")
public class ExportChangePortGroupService extends ViPRService {

    @Param(HOST)
    protected URI hostId;
    
    @Param(VIRTUAL_ARRAY)
    protected URI virtualArrayId;
    
    @Param(EXPORT)
    protected URI exportId;
    
    @Param(STORAGE_SYSTEM)
    protected URI storageSystemId;
    
    @Param(CURRENT_PORT_GROUP)
    protected URI currentPortGroupId;
    
    @Param(value = CHANGE_PORT_GROUP, required = true)
    protected URI newPortGroupId;
    
    @Param(SUSPEND_WAIT)
    protected boolean suspendWait;
    
    @Override
    public void precheck() throws Exception {
        
        if (currentPortGroupId.equals(newPortGroupId)) {
            ExecutionUtils.fail("failTask.exportPortGroupChange.precheck", args());
        }
        
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
        
        Task<ExportGroupRestRep> task = execute(new ExportChangePortGroup(exportId, newPortGroupId, suspendWait));        
        addAffectedResource(task);
    }
}

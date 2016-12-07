/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jetty.util.log.Log;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class ExportPortRebalanceCompleter extends ExportTaskCompleter{
    
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ExportMaskAddPathsCompleter.class);
    private static final String EXPORT_PORT_REBALANCE_MSG = "Path adjustment to ExportGroup %s";
    private static final String EXPORT_PORT_REBALANCE_FAILED_MSG = "Failed path adjustment to ExportGroup %s";
    
    private ExportPathParams newPathParam;
    private URI systemURI;
    
    public ExportPortRebalanceCompleter(URI systemURI, URI id, String opId, ExportPathParams exportPathParam) {
        super(ExportGroup.class, id, opId);
        this.newPathParam = exportPathParam;
        this.systemURI = systemURI;
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            super.complete(dbClient, status, coded);
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            
            //TODO update volumes exportPathParams
            Operation operation = new Operation();
            switch (status) {
                case error:
                    operation.error(coded);
                    break;
                case ready:
                    operation.ready();
                    updateVolumeExportPathParam(dbClient, exportGroup);
                    break;
                case suspended_no_error:
                    operation.suspendedNoError();
                    break;
                case suspended_error:
                    operation.suspendedError(coded);
                    break;
                default:
                    break;
            }
            exportGroup.getOpStatus().updateTaskStatus(getOpId(), operation);
            dbClient.updateObject(exportGroup);

            log.info(String.format("Done Export path adjuestment - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));

            recordBlockExportOperation(dbClient, OperationTypeEnum.EXPORT_PATH_ADJUSTMENT, status, eventMessage(status, exportGroup),
                    exportGroup);
        } catch (Exception e) {
            log.error(String.format("Failed updating status for ExportMaskCreate - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);

        }
    }
    
    private String eventMessage(Operation.Status status, ExportGroup exportGroup) {
        return (status == Operation.Status.ready) ?
                String.format(EXPORT_PORT_REBALANCE_MSG, exportGroup.getLabel()) :
                String.format(EXPORT_PORT_REBALANCE_FAILED_MSG, exportGroup.getLabel());
    }
    
    /**
     * Update export group pathParameters for the impacted volumes
     * 
     * @param dbClient
     * @param exportGroup
     */
    private void updateVolumeExportPathParam(DbClient dbClient, ExportGroup exportGroup) {
        log.info("updating path param map.");
        StringMap volumes = exportGroup.getVolumes();
        StringMap existingPathMap = exportGroup.getPathParameters();
        List<URI> impactedVolumes = new ArrayList<URI>();
        Map<URI, List<URI>> vpoolVolumes = new HashMap<URI, List<URI>>();
        Map<URI, List<URI>> volumePath = new HashMap<URI, List<URI>> ();
        if (volumes != null && !volumes.isEmpty()) {
            Set<String> volSet = volumes.keySet();
            for (String volId : volSet) {
                URI volURI = URI.create(volId);
                Volume volume = dbClient.queryObject(Volume.class, volURI);
                if (volume != null && volume.getStorageController().equals(systemURI)) {
                    log.info(String.format("Checking for the volume %s", volume.getLabel()));
                    // Check if the volume is in the pathMap
                    String pathParm = existingPathMap.get(volId);
                    if (pathParm != null) {
                        URI pathId  = URI.create(pathParm);
                        List<URI> vols = volumePath.get(pathId);
                        if (vols == null) {
                            vols = new ArrayList<URI>();
                            volumePath.put(pathId, vols);
                        }
                        vols.add(volURI);
                    } else {
                    
                        // check if the volumes' export path parameters are the same
                        URI vpId = volume.getVirtualPool();
                        List<URI> vols = vpoolVolumes.get(vpId);
                        if (vols == null) {
                            vols = new ArrayList<URI>();
                            vpoolVolumes.put(vpId, vols);
                        }
                        vols.add(volURI);
                    }
                }
            }
        }
        if (!vpoolVolumes.isEmpty()) {
            log.info("check for vpool");
            for (Map.Entry<URI, List<URI>> entry : vpoolVolumes.entrySet()) {
                URI vpoolId = entry.getKey();
                VirtualPool vpool = dbClient.queryObject(VirtualPool.class, vpoolId);
                if (vpool != null) {
                    log.info(String.format("vpool path param : %d, %d, %d", vpool.getMinPaths(), vpool.getNumPaths(), vpool.getPathsPerInitiator()));
                    log.info(String.format("New path param %d, %d, %d", newPathParam.getMinPaths(), newPathParam.getMaxPaths(), newPathParam.getPathsPerInitiator()));
                    if (vpool.getMinPaths() != newPathParam.getMinPaths() ||
                            vpool.getNumPaths() != newPathParam.getMaxPaths() ||
                            vpool.getPathsPerInitiator() != newPathParam.getPathsPerInitiator()) {
                        impactedVolumes.addAll(entry.getValue());
                    }
                }
            }
        }
        if (!volumePath.isEmpty()) {
            for (Map.Entry<URI, List<URI>> entry : volumePath.entrySet()) {
                URI pathParamURI = entry.getKey();
                ExportPathParams pathParam = dbClient.queryObject(ExportPathParams.class, pathParamURI);
                if (pathParam == null || pathParam.getMinPaths() != newPathParam.getMinPaths() ||
                        pathParam.getMaxPaths() != newPathParam.getMaxPaths() ||
                        pathParam.getPathsPerInitiator() != newPathParam.getPathsPerInitiator()) {
                    //remove the path entry for this volume
                    for (URI volURI : entry.getValue()) {
                        exportGroup.removeFromPathParameters(volURI);
                    }
                    // If there are no more entries for the given ExportPathParam, mark it for deletion
                    if (!exportGroup.getPathParameters().containsValue(pathParamURI.toString()) &&
                            pathParam != null) {
                        dbClient.markForDeletion(pathParam);
                    }
                    impactedVolumes.addAll(entry.getValue());
                }
                
            }
        }
        if (!impactedVolumes.isEmpty()) {
            ExportPathParams pathParam = new ExportPathParams();
            pathParam.setMaxPaths(newPathParam.getMaxPaths());
            pathParam.setMinPaths(newPathParam.getMinPaths());
            pathParam.setPathsPerInitiator(newPathParam.getPathsPerInitiator());
            pathParam.setExportGroupType(exportGroup.getType());
            pathParam.setLabel(exportGroup.getLabel());
            if (newPathParam.getStoragePorts() != null) {
                pathParam.setStoragePorts(newPathParam.getStoragePorts());
            }
            pathParam.setExplicitlyCreated(false);
            
            pathParam.setId(URIUtil.createId(ExportPathParams.class));
            pathParam.setInactive(false);
            dbClient.createObject(pathParam);
            for (URI volId : impactedVolumes) {
                exportGroup.addToPathParameters(volId, pathParam.getId());
            }
            
        }
        dbClient.updateObject(exportGroup);
    }

}

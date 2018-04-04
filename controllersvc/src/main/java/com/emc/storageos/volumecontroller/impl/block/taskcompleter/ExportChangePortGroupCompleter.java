/*
 * Copyright (c) 2017 Dell-EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class ExportChangePortGroupCompleter extends ExportTaskCompleter {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ExportChangePortGroupCompleter.class);
    private static final String EXPORT_CHANGE_PORT_GROUP_MSG = "Change port group to ExportGroup %s";
    private static final String EXPORT_CHANGE_PORT_GROUP_FAILED_MSG = "Failed change port group to ExportGroup %s";
    private static final String EXPORT_CHANGE_PORT_GROUP_SUSPENDED_MSG = "Change port group to ExportGroup %s suspended";

    private URI newPortGroup;
    private URI systemURI;
    private Set<URI> affectedExportGroups;

    public ExportChangePortGroupCompleter(URI systemURI, URI id, String opId, URI newPortGroup) {
        super(ExportGroup.class, id, opId);
        this.newPortGroup = newPortGroup;
        this.systemURI = systemURI;
    }

    public void setAffectedExportGroups(Set<URI> exportGroups) {
        this.affectedExportGroups = exportGroups;
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            String eventMessage = null;
            log.info("Change port group completer:" + status.name());
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            Operation operation = new Operation();
            switch (status) {
                case error:
                    operation.error(coded);
                    eventMessage = String.format(EXPORT_CHANGE_PORT_GROUP_FAILED_MSG, exportGroup.getLabel());
                    break;
                case ready:
                    operation.ready();
                    updateVolumeExportPathParam(dbClient);
                    eventMessage = String.format(EXPORT_CHANGE_PORT_GROUP_MSG, exportGroup.getLabel());
                    break;
                case suspended_no_error:
                    operation.suspendedNoError();
                    eventMessage = String.format(EXPORT_CHANGE_PORT_GROUP_SUSPENDED_MSG, exportGroup.getLabel());
                    break;
                case suspended_error:
                    operation.suspendedError(coded);
                    eventMessage = String.format(EXPORT_CHANGE_PORT_GROUP_SUSPENDED_MSG, exportGroup.getLabel());
                    break;
                default:
                    break;
            }

            exportGroup.getOpStatus().updateTaskStatus(getOpId(), operation);
            dbClient.updateObject(exportGroup);

            log.info(String.format("Done Export change port group - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));

            recordBlockExportOperation(dbClient, OperationTypeEnum.EXPORT_CHANGE_PORT_GROUP, status, eventMessage, exportGroup);
        } catch (Exception e) {
            log.error(String.format("Failed updating status for Export change port group - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);

        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    /**
     * Update export group pathParameters for the impacted volumes
     * 
     * @param dbClient
     */
    private void updateVolumeExportPathParam(DbClient dbClient) {
        log.info("updating path param map.");
        if (affectedExportGroups == null || affectedExportGroups.isEmpty()) {
            // not export group is affected, return
            return;
        }
        for (URI egURI : affectedExportGroups) {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, egURI);
            log.info(String.format("Updating export group %s", exportGroup.getId().toString()));
            StringMap volumes = exportGroup.getVolumes();
            StringMap existingPathMap = exportGroup.getPathParameters();
            List<URI> impactedVolumes = new ArrayList<URI>();
            Map<URI, List<URI>> volumePath = new HashMap<URI, List<URI>>();
            if (volumes == null || volumes.isEmpty()) {
                continue;
            }
            Set<String> volSet = volumes.keySet();
            for (String volId : volSet) {
                URI volURI = URI.create(volId);
                Volume volume = dbClient.queryObject(Volume.class, volURI);
                if (volume == null || !volume.getStorageController().equals(systemURI)) {
                    continue;
                }
                log.info(String.format("Checking for the volume %s", volume.getLabel()));
                // Check if the volume is in the pathMap
                String pathParm = existingPathMap.get(volId);
                if (pathParm != null) {
                    URI pathId = URI.create(pathParm);
                    List<URI> vols = volumePath.get(pathId);
                    if (vols == null) {
                        vols = new ArrayList<URI>();
                    }
                    vols.add(volURI);
                    volumePath.put(pathId, vols);
                } else {
                    // No path param associated with the volume yet
                    impactedVolumes.add(volURI);
                }
            }

            if (!volumePath.isEmpty()) {
                for (Map.Entry<URI, List<URI>> entry : volumePath.entrySet()) {
                    URI pathParamURI = entry.getKey();
                    ExportPathParams pathParam = dbClient.queryObject(ExportPathParams.class, pathParamURI);
                    if (pathParam != null) {
                        pathParam.setPortGroup(newPortGroup);
                        dbClient.updateObject(pathParam);
                    }
                }
            }
            if (!impactedVolumes.isEmpty()) {
                ExportPathParams pathParam = new ExportPathParams();
                pathParam.setLabel(exportGroup.getLabel());
                pathParam.setExplicitlyCreated(false);

                pathParam.setId(URIUtil.createId(ExportPathParams.class));
                pathParam.setPortGroup(newPortGroup);
                pathParam.setInactive(false);
                dbClient.createObject(pathParam);
                for (URI volId : impactedVolumes) {
                    exportGroup.addToPathParameters(volId, pathParam.getId());
                }
            }
            dbClient.updateObject(exportGroup);
        }
    }

}
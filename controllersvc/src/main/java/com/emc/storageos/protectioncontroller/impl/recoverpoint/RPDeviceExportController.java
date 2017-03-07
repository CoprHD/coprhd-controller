/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.protectioncontroller.impl.recoverpoint;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.protectioncontroller.ProtectionExportController;
import com.emc.storageos.volumecontroller.impl.block.ExportWorkflowUtils;
import com.emc.storageos.workflow.Workflow;

public class RPDeviceExportController implements ProtectionExportController {
    private final DbClient dbClient;
    private final ExportWorkflowUtils wfUtils;

    private static final Logger log = LoggerFactory.getLogger(RPDeviceExportController.class);

    public RPDeviceExportController(DbClient dbClient, ExportWorkflowUtils wfUtils) {
        this.dbClient = dbClient;
        this.wfUtils = wfUtils;
    }

    @Override
    public String addStepsForExportGroupCreate(Workflow workflow, String wfGroupId, String waitFor, URI export,
            Map<URI, Integer> objectsToAdd, URI storageUri, List<URI> initiatorURIs) {

        // Extract all BlockSnapshots with a protection system from the list of objects to export and
        // sort them by protection system. If we have objects with a protection system set, the
        // export needs to be handled by the associated protection controller, and NOT the block
        // controller.
        Map<URI, Map<URI, Integer>> protectionMap = sortSnapshotsByProtectionSystem(objectsToAdd);

        if (!protectionMap.isEmpty()) {
            for (URI protectionSystemUri : protectionMap.keySet()) {
                // Obtain subset of objects to export that reference the current protection controller
                Map<URI, Integer> objectsToExportWithProtection = protectionMap.get(protectionSystemUri);
                log.info(String
                        .format(
                                "Generating exportGroupCreate steps for objects %s associated with protection system [%s] and storage system [%s]",
                                objectsToExportWithProtection.keySet(), protectionSystemUri, storageUri));
                waitFor = wfUtils.
                        generateExportGroupCreateWorkflow(workflow, null, waitFor,
                                protectionSystemUri, export, objectsToExportWithProtection, initiatorURIs);

                // Reconcile the list of objects being added by removing those that have been exported
                for (URI blockObjectUri : objectsToExportWithProtection.keySet()) {
                    objectsToAdd.remove(blockObjectUri);
                }
            }
        }

        return waitFor;
    }

    @Override
    public String addStepsForExportGroupRemoveVolumes(Workflow workflow, String wfGroupId, String waitFor, URI export,
            Map<URI, Integer> objectsToRemove, URI storageUri) {
        // Obtain a Map of BlockSnapshots that are associated to a protection system.
        Map<URI, Map<URI, Integer>> removeBlockObjectsByProtectionSystem = sortSnapshotsByProtectionSystem(objectsToRemove);
        if (!removeBlockObjectsByProtectionSystem.isEmpty()) {
            // For each protection system, create the export group remove volume steps for the associated BlockSnapshot objects.
            for (URI protectionSystemUri : removeBlockObjectsByProtectionSystem.keySet()) {
                List<URI> objectsToRemoveWithProtection = new ArrayList<URI>(removeBlockObjectsByProtectionSystem.get(
                        protectionSystemUri).keySet());
                log.info(String
                        .format(
                                "Generating exportGroupRemoveVolumes step for objects %s associated with protection system [%s] and storage system [%s]",
                                objectsToRemoveWithProtection, protectionSystemUri, storageUri));
                waitFor = wfUtils.generateExportGroupRemoveVolumes(workflow, wfGroupId, waitFor, protectionSystemUri, export,
                        objectsToRemoveWithProtection);
                // Reconcile the primary list of unexport objects by removing all BlockSnapshots associated with the current
                // protection system.
                for (URI blockObjectUri : objectsToRemoveWithProtection) {
                    objectsToRemove.remove(blockObjectUri);
                }
            }
        }

        return waitFor;
    }

    @Override
    public String addStepsForExportGroupAddVolumes(Workflow workflow, String wfGroupId, String waitFor, URI export,
            Map<URI, Integer> objectsToAdd, URI storageUri) {
        // Obtain a Map of BlockSnapshots that are associated to a protection system.
        Map<URI, Map<URI, Integer>> addedBlockObjectsByProtectionSystem = sortSnapshotsByProtectionSystem(objectsToAdd);
        if (!addedBlockObjectsByProtectionSystem.isEmpty()) {
            // For each protection system, create the export group add volume steps for the associated BlockSnapshot objects.
            for (URI protectionSystemUri : addedBlockObjectsByProtectionSystem.keySet()) {
                Map<URI, Integer> objectsToAddWithProtection = addedBlockObjectsByProtectionSystem.get(protectionSystemUri);
                log.info(String
                        .format(
                                "Generating exportGroupAddVolumes step for objects %s associated with protection system [%s] and storage system [%s]",
                                objectsToAddWithProtection.keySet(), protectionSystemUri, storageUri));
                waitFor = wfUtils.generateExportGroupAddVolumes(workflow, wfGroupId, waitFor, protectionSystemUri, export,
                        objectsToAddWithProtection);

                // Reconcile the primary list of objects to export by removing all BlockSnapshots associated with the current
                // protection system.
                for (URI blockObjectUri : objectsToAddWithProtection.keySet()) {
                    objectsToAdd.remove(blockObjectUri);
                }
            }
        }

        return waitFor;
    }

    /**
     * Extracts RP BlockSnapshot objects from a list of BlockObjects and sort them by protection system. If the
     * protection system is not set, the object is omitted from the sorted Map.
     *
     * @param blockObjectsMap
     * @return a Map of BlockSnapshot objects sorted by protection system
     */
    private Map<URI, Map<URI, Integer>> sortSnapshotsByProtectionSystem(Map<URI, Integer> blockObjectsMap) {
        Map<URI, Map<URI, Integer>> protectionMap = new HashMap<URI, Map<URI, Integer>>();

        if (blockObjectsMap != null) {
            for (URI blockObjectUri : blockObjectsMap.keySet()) {
                BlockObject bo = BlockObject.fetch(dbClient, blockObjectUri);
                // Only grab the RP BlockSnapshots
                if (bo != null && bo instanceof BlockSnapshot && !NullColumnValueGetter.isNullURI(bo.getProtectionController())) {
                    if (protectionMap.get(bo.getProtectionController()) == null) {
                        protectionMap.put(bo.getProtectionController(), new HashMap<URI, Integer>());
                    }
                    protectionMap.get(bo.getProtectionController()).put(blockObjectUri, blockObjectsMap.get(blockObjectUri));
                }
            }
        }

        return protectionMap;
    }
}

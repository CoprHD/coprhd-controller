/*
 * Copyright (c) 2015. EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.util.ExportUtils;

/**
 * This class has a template function that encapsulates the selection of ExportMasks based on volumes. It requires that
 * a context parameter with all relevant data be passed in. Derived classes should implement the abstract methods to
 * customize the selection
 */
public abstract class VmaxVolumeToExportMaskRuleApplicator {
    private static final Logger log = LoggerFactory.getLogger(VmaxVolumeToExportMaskRuleApplicator.class);
    private final DbClient dbClient;
    protected final VmaxVolumeToExportMaskApplicatorContext context;

    public VmaxVolumeToExportMaskRuleApplicator(DbClient dbClient, VmaxVolumeToExportMaskApplicatorContext context) {
        this.context = context;
        this.dbClient = dbClient;
    }

    abstract public void postApply(List<URI> initiatorsForResource, Map<URI, Map<URI, Integer>> initiatorsToVolumes) throws Exception;

    abstract public boolean applyRules(Map<URI, Map<URI, Integer>> initiatorsToVolumes);

    /**
     * This is a template routine. The expectation is that the derived classes will
     * fill in the abstract methods that are called within it
     */
    public void run() throws Exception {
        // Basic input: initiators, their associated ExportMasks, and volumes to export
        Map<String, Set<URI>> initiatorToExportMaskPlacementMap = context.initiatorToExportMaskPlacementMap;
        AbstractDefaultMaskingOrchestrator.InitiatorHelper initiatorHelper = context.initiatorHelper;
        Map<URI, Integer> volumeMap = context.volumeMap;

        // Master policy map can be used as additional contextual information
        context.exportMaskToPolicy = createPolicyMap();

        // Determine the compute resources based on the initiators and then group by that resource name/ID
        Map<String, Set<URI>> computeResourceInitiatorsMap = AbstractDefaultMaskingOrchestrator.createResourceMaskMap(
                initiatorHelper.getPortNameToInitiatorURI(), initiatorHelper.getResourceToInitiators(), initiatorToExportMaskPlacementMap);

        // Foreach compute resource ...
        for (Map.Entry<String, Set<URI>> resourceEntry : computeResourceInitiatorsMap.entrySet()) {
            String computeResource = resourceEntry.getKey();
            // Get its initiators & map them to the volumes to export ...
            List<URI> initiatorsForResource = initiatorHelper.getResourceToInitiators().get(computeResource);
            Map<URI, Map<URI, Integer>> initiatorsToVolumes = mapComputeResourceInitiatorsToVolumes(initiatorsForResource, volumeMap);
            // Run the customized rules referencing the initiators & volumes ...
            if (applyRules(initiatorsToVolumes)) {
                // If the rules were successfully applies, run the post operation
                postApply(initiatorsForResource, initiatorsToVolumes);
            } else {
                // The operation failed, mark it as failed and exit
                context.resultSuccess = false;
                return;
            }
        }

        // If we got here, there were no applyRules() failures, so success!
        context.resultSuccess = true;
    }

    /**
     * Using the contextual information produce a mapping of ExportMask to its ExportMaskPolicy.
     *
     * @return Map of ExportMask object to ExportMaskPolicy
     */
    private Map<ExportMask, ExportMaskPolicy> createPolicyMap() {
        // Translate the ExportMask URI to ExportMaskPolicy cache to a mapping of ExportMask object to ExportMaskPolicy
        Map<ExportMask, ExportMaskPolicy> policyMap = new HashMap<>();
        Iterator<ExportMask> exportMasks = dbClient.queryIterativeObjects(ExportMask.class, context.exportMaskURIToPolicy.keySet());
        while (exportMasks.hasNext()) {
            ExportMask mask = exportMasks.next();
            // Check for NO_VIPR. If found, avoid this mask.
            if (mask.getMaskName() != null && mask.getMaskName().toUpperCase().contains(ExportUtils.NO_VIPR)) {
                log.info(
                        String.format("ExportMask %s disqualified because the name contains %s (in upper or lower case) to exclude it",
                                mask.getMaskName(), ExportUtils.NO_VIPR));
                continue;
            }
            ExportMaskPolicy policy = context.exportMaskURIToPolicy.get(mask.getId());
            if (policy != null) {
                policyMap.put(mask, policy);
            } else {
                log.error("Could not find an ExportMaskPolicy for {} ({})", mask.getMaskName(), mask.getId());
            }
        }
        return policyMap;
    }

    /**
     * Create a mapping of the initiators to all the volumes
     *
     * @param initiatorsForResource [IN] - Initiators URIs that belong to a particular resource
     * @param volumeMap [IN] - Volumes that need to mapped to the initiators
     * @return Map of the Initiator URIs to Volumes
     */
    private Map<URI, Map<URI, Integer>> mapComputeResourceInitiatorsToVolumes(List<URI> initiatorsForResource,
            Map<URI, Integer> volumeMap) {
        Map<URI, Map<URI, Integer>> initiatorsToVolumes = new HashMap<>();
        for (URI initiatorId : initiatorsForResource) {
            Map<URI, Integer> vols = initiatorsToVolumes.get(initiatorId);
            if (vols == null) {
                vols = new HashMap<>();
                initiatorsToVolumes.put(initiatorId, vols);
            }
            vols.putAll(volumeMap);
        }
        return initiatorsToVolumes;
    }
}

/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.impl.block.MaskingOrchestrator;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.Workflow;
import com.google.common.base.Joiner;

public class ExportPathUpdater {
    private static final Logger _log = LoggerFactory.getLogger(ExportPathUpdater.class);
    private DbClient _dbClient;

    public ExportPathUpdater(DbClient dbClient) {
        this._dbClient = dbClient;
    }

    /**
     * This routine is called by the API service to validate that the change path parameters
     * call will succeed. For now only the following are allowed:
     * A. Increasing maxPaths with pathsPerInitiator the same.
     * The following are disallowed:
     * X. Decreasing maxPaths
     * Y. Changing pathsPerInitiator.
     * 
     * @param storageURI
     * @param exportGroupURI
     * @param volume (BlockObject)
     * @param newParam ExportPathParam new parameters being proposed
     */
    private void validateChangePathParams(URI storageURI, URI exportGroupURI,
            BlockObject volume, ExportPathParams newParam) {
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                exportGroupURI);
        _log.info(String.format("Validating path parameters for volume %s (%s)",
                volume.getLabel(), volume.getId()));
        Set<URI> volumeURISet = new HashSet<URI>();
        volumeURISet.add(volume.getId());
        
        // Check that the ExportGroup has not overridden the Vpool path parameters.
        if (exportGroup.getPathParameters().containsKey(volume.getId().toString())) {
            // Cannot do a Vpool path change because parameters have been set in Export Group 
            _log.info(String.format(
              "No changes will be made to ExportGroup %s (%s) because it has explicit path parameters overiding the Vpool", 
              exportGroup.getLabel(), exportGroup.getId()));
            return;
        }

        // Search through the Export Masks looking for any containing this Volume.
        // We only process ViPR created Export Masks, others are ignored.
        List<ExportMask> masks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, storageURI);
        for (ExportMask mask : masks) {
            if (!mask.hasVolume(volume.getId())) {
                continue;
            }
            if (mask.getCreatedBySystem() == false || mask.getZoningMap() == null) {
                _log.info(String.format("ExportMask %s not ViPR created, and will be ignored", mask.getMaskName()));
                continue;
            }
            ExportPathParams maskParam = BlockStorageScheduler
                    .calculateExportPathParamForExportMask(_dbClient, mask);
            if (newParam.getPathsPerInitiator() > maskParam.getPathsPerInitiator()) {
                // We want to increase pathsPerInitiator
                throw APIException.badRequests.cannotChangeVpoolPathsPerInitiator(exportGroup.getLabel(), mask.getMaskName());
            } else if (newParam.getMaxPaths() < maskParam.getMaxPaths()) {
                // We want to decreates maxPaths
                throw APIException.badRequests.cannotReduceVpoolMaxPaths(exportGroup.getLabel(), mask.getMaskName());
            }
        }
    }

    /**
     * This routine is called by the API service to validate that the change path parameters
     * call will succeed. It locates all the ExportGroups that are exporting the volume.
     * 
     * @param volume (BlockObject)
     * @param newParam ExportPathParams new parameters being proposed
     */
    public void validateChangePathParams(URI volumeURI, ExportPathParams newParam) {
        BlockObject volume = BlockObject.fetch(_dbClient, volumeURI);
        _log.info(String.format("Validating path parameters for volume %s (%s) new path parameters %s",
                volume.getLabel(), volume.getId(), newParam.toString()));
        // Locate all the ExportMasks containing the given volume, and their Export Group.
        Map<ExportMask, ExportGroup> maskToGroupMap =
                ExportUtils.getExportMasks(volume, _dbClient);
        // These steps are serialized, but there is no requirement that they be serialized
        // that I know of. It does make it easier to figure out what is going on in the logs.
        for (ExportGroup exportGroup : maskToGroupMap.values()) {
            validateChangePathParams(volume.getStorageController(), exportGroup.getId(),
                    volume, newParam);
        }
    }

    /**
     * Look through the ExportMask for initiators that are not mapped to any targets.
     * This is indicated by the initiators list containing an initiator that is not
     * present in the zoningMap as a key.
     * Also, we look in the host to see if there are any Initiators
     * that could be added to the Export Mask.
     * If found, the ExportMask and ExportGroup are updated.
     * 
     * @param mask ExportMask
     * @return List<URI> list of Initiator URIs
     */
    private List<URI> getUnusedInitiators(ExportGroup group, ExportMask mask) {
        List<URI> unusedInitiators = new ArrayList<URI>();
        List<URI> hostURIs = new ArrayList<URI>();
        for (String initiatorId : mask.getInitiators()) {
            Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(initiatorId));
            if (initiator == null || initiator.getInactive()) {
                continue;
            }
            if (!NullColumnValueGetter.isNullURI(initiator.getHost())) {
                hostURIs.add(initiator.getHost());
            }
            if (mask.getZoningMap().get(initiatorId) == null) {
                unusedInitiators.add(initiator.getId());
            }
        }

        // Any initiators not in the exportMask should be checked to see if they can be added
        for (URI hostURI : hostURIs) {
            URIQueryResultList initiatorUris = new URIQueryResultList();
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getContainedObjectsConstraint(
                            hostURI, Initiator.class, "host"), initiatorUris);
            List<Initiator> initiators = _dbClient.queryObject(Initiator.class, initiatorUris);

            for (Initiator initiator : initiators) {
                if (!mask.hasInitiator(initiator.getId().toString())) {
                    StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, mask.getStorageDevice());
                    // Determine what varrays we can use from the ExportGroup
                    List<URI> varrays = new ArrayList<URI>();
                    varrays.add(group.getVirtualArray());
                    if (group.hasAltVirtualArray(storageSystem.getId().toString())) {
                        URI altVarray = URI.create(group.getAltVirtualArrays().get(storageSystem.getId().toString()));
                        varrays.add(altVarray);
                    }
                    // Check to see if the initiator is connected through the storage array.
                    if (ConnectivityUtil.isInitiatorConnectedToStorageSystem(
                            initiator, storageSystem, varrays, _dbClient)) {
                        _log.info(String.format("Adding host %s initiator %s (%s) to ExportMask %s",
                                initiator.getHostName(), initiator.getInitiatorPort(), initiator.getId(), mask.getMaskName()));
                        // Add the initiator into the Export Mask and unusedInitiators list.
                        mask.addInitiator(initiator);
                        mask.addToUserCreatedInitiators(initiator);
                        _dbClient.updateAndReindexObject(mask);
                        group.addInitiator(initiator);
                        _dbClient.updateAndReindexObject(group);
                        unusedInitiators.add(initiator.getId());
                    }
                }
            }
        }
        _log.info("Unused initiators that will be provisioned: ", Joiner.on(",").join(unusedInitiators));
        return unusedInitiators;
    }

    /**
     * Generates the workflow steps to change path parameters of an ExportGroup.
     * The volume is used to determine the path parameters (from its Vpool).
     * 
     * @param workflow
     * @param blockScheduler
     * @param orchestrator
     * @param storage -- StorageSystem
     * @param exportGroup
     * @param volume
     * @param token -- Task token
     * @throws Exception
     */
    public void generateExportGroupChangePathParamsWorkflow(
            Workflow workflow, BlockStorageScheduler blockScheduler,
            MaskingOrchestrator orchestrator,
            StorageSystem storage, ExportGroup exportGroup,
            BlockObject volume, String token) throws Exception {
        Set<URI> volumeURISet = new HashSet<URI>();
        volumeURISet.add(volume.getId());
        
        // Check that the ExportGroup has not overridden the Vpool path parameters.
        if (exportGroup.getPathParameters().containsKey(volume.getId().toString())) {
            // Cannot do a Vpool path change because parameters have been set in Export Group 
            _log.info(String.format(
              "No changes will be made to ExportGroup %s (%s) because it has explicit path parameters overiding the Vpool", 
              exportGroup.getLabel(), exportGroup.getId()));
            return;
        }
        
        ExportPathParams newParam = blockScheduler.calculateExportPathParamForVolumes(
                volumeURISet, 0, storage.getId(), exportGroup.getId());
        _log.info("New path parameters requested: " +  newParam.toString());

        // Search through the Export Masks looking for any containing this Volume.
        // We only process ViPR created Export Masks, others are ignored.
        List<ExportMask> masks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, storage.getId());
        for (ExportMask mask : masks) {
            if (!mask.hasVolume(volume.getId())) {
                continue;
            }
            // We don't attempt to update the path parameters for Export Masks that were not
            // ViPR created, or if the don't have a zoning Map. The later means that ViPR 1.0
            // created Export Masks will not be updated, because the zoningMap was not
            // introduced until ViPR 1.1.
            if (mask.getCreatedBySystem() == false || mask.getZoningMap() == null) {
                _log.info(String.format("ExportMask %s not ViPR created, and will be ignored", mask.getMaskName()));
                continue;
            }
            ExportPathParams maskParam = BlockStorageScheduler
                    .calculateExportPathParamForExportMask(_dbClient, mask);
            _log.info(String.format(
              "Existing mask %s (%s) path parameters: %s", 
              mask.getMaskName(), mask.getId(),  maskParam.toString()));

            if (newParam.getPathsPerInitiator() > maskParam.getPathsPerInitiator()) {
                _log.info("Increase paths per initiator not supported");
                // We want to increase paths per initiator
                // Not supported yet, code will be added here.
            } else if (newParam.getMaxPaths() > maskParam.getMaxPaths()) {
                // We want to increase MaxPaths.
                // Determine the currently unused Initiators.
                List<URI> unusedInitiators = getUnusedInitiators(exportGroup, mask);
                if (!unusedInitiators.isEmpty()) {
                    _log.info(String.format("Increasing max_paths from %d to %d",
                            maskParam.getMaxPaths(), newParam.getMaxPaths()));
                    orchestrator.increaseMaxPaths(workflow, storage, exportGroup,
                            mask, unusedInitiators, token);
                }
            } else if (newParam.getMaxPaths() < maskParam.getMaxPaths()) {
                _log.info("Decrease max paths not supported");
                // We want to lower MaxPaths. See if no other volume has a higher MaxPaths.
                // Not supported yet, code will be added here.
            }
        }
    }
}

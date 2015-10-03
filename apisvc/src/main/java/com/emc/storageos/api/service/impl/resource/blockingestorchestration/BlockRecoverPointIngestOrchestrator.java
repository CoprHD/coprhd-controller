/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.ProtectionSet.ProtectionStatus;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet.SupportedCGInformation;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.block.VolumeExportIngestParam;

/**
 * RecoverPoint Ingestion
 * 
 * Ingestion of RecoverPoint is done one volume at a time, like any other ingestion.
 * The goal is to allow ingestion to occur in any order:  journals, targets, and sources.
 * BlockConsistencyGroup and ProtectionSet objects are not created until all volumes associated
 * with the RP CG are ingested.  All Volume objects should be flagged to not appear in the UI
 * during this intermediate phase.  (NO_PUBLIC_ACCESS or similar)
 * 
 * Commmon RP Volume Ingestion Steps:
 * - A Volume object is created with respective personality field (SOURCE/JOURNAL/METADATA) filled-in
 * - The UnManagedVolume reference is removed from the UnManagedProtectionSet
 * - A ManagedVolume reference is added to the UnManagedProtectionSet
 * - The export mask that is attached to the ProtectionSystem in the UnManagedProtectionSet that contains this volume is ingested
 * 
 * Journal Ingestion:
 * - RP attributes are added to Volume: RP copy name, protection system, etc
 * 
 * Source Ingestion:
 * - RP attributes are added to Volume: RP copy name, Replication Set name, protection system, etc.
 * - Any target volume already ingested (in the unmanaged source's MANAGED_TARGET list) is added to source volume's RP Target list
 * - Any target volume not yet ingested, remove unmanaged source volume ID from unmanaged target volume
 * - Any target volume not yet ingested, add managed source volume ID from unmanaged target volume
 * 
 * Target Ingestion:
 * - RP attributes are added to Volume: RP copy name, Replication Set name, protection system, etc.
 * - If source volume is already ingested, add volume to source volume's RP Target list 
 * - If source volume is not yet ingested, remove unmanaged target volume from unmanaged source volume's unmanaged target list
 * - If source volume is not yet ingested, add managed target volume to managed source volume's managed target list
 * 
 * The last volume in the UnManagedProtection set to be ingested triggers full RP CG ingestion:
 * 
 * Criteria for Full Ingestion of an RP CG:
 * - All Journals, Sources, and Targets associated with the UnManagedProtectionSet are now Managed volumes
 * - Validation occurs where needed, such as ensuring that the journals and targets are assigned to the right vpools (TODO)
 * - BlockConsistencyGroup and ProtectionSet objects are created and all ingested volumes therein are updated with references to them.
 * 
 */
public class BlockRecoverPointIngestOrchestrator extends BlockIngestOrchestrator {

    private static final Logger _logger = LoggerFactory.getLogger(BlockRecoverPointIngestOrchestrator.class);

    // The ingest strategy factory, used for ingesting the volumes using the appropriate orchestrator (VPLEX, block, etc)
    private IngestStrategyFactory ingestStrategyFactory;

    public void setIngestStrategyFactory(IngestStrategyFactory ingestStrategyFactory) {
        this.ingestStrategyFactory = ingestStrategyFactory;
    }

    @Override
    protected void checkUnmanagedVolumeReplicas(UnManagedVolume unmanagedVolume) {
        return;
    }

    @Override
    public <T extends BlockObject> T ingestBlockObjects(List<URI> systemCache, List<URI> poolCache, StorageSystem system, UnManagedVolume unManagedVolume, 
            VirtualPool vPool, VirtualArray virtualArray, Project project, TenantOrg tenant, List<UnManagedVolume> unManagedVolumesSuccessfullyProcessed, 
            Map<String, BlockObject> createdObjectMap, Map<String, List<DataObject>> updatedObjectMap, boolean unManagedVolumeExported, Class<T> clazz, 
            Map<String, StringBuffer> taskStatusMap, String vplexIngestionMethod) throws IngestionException {
        String volumeNativeGuid = unManagedVolume.getNativeGuid().replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                VolumeIngestionUtil.VOLUME);
        BlockObject blockObject = VolumeIngestionUtil.checkIfVolumeExistsInDB(volumeNativeGuid, _dbClient);

        // Validation checks on the unmanaged volume we're trying to ingest
        validateUnManagedVolumeProperties(unManagedVolume, virtualArray, vPool, project);

        // Make sure there's an unmanaged protection set
        UnManagedProtectionSet umpset = getUnManagedProtectionSet(unManagedVolume);
        if (umpset == null) {
            _logger.warn("No unmanaged protection set could be found for unmanaged volume: " + unManagedVolume.getNativeGuid() + " Please run unmanaged CG discovery of registered protection system");
            throw IngestionException.exceptions.unManagedProtectionSetNotFound(unManagedVolume.getNativeGuid());
        }

        // Test ingestion status message
        _logger.info("Printing Ingestion Report before Ingestion Attempt");
        _logger.info(getRPIngestionStatus(umpset));
        
        // This ingestion orchestrator only deals with Volume objects.  (snapshots, mirrors, clones aren't protected by RP)
        if (blockObject != null && !(blockObject instanceof Volume)) {
            _logger.error("Ingesting a non-volume object in RecoverPoint is not allowed: " + blockObject.getId().toString());
            throw IngestionException.exceptions.rpIngestingNonVolumeObject(unManagedVolume.getNativeGuid());
        }
        
        Volume volume = (Volume)blockObject;
        // Perform RP-specific volume ingestion
        volume = performRPVolumeIngestion(project, virtualArray, vPool, system, unManagedVolume, systemCache, poolCache, tenant,
                unManagedVolumesSuccessfullyProcessed, createdObjectMap, updatedObjectMap, taskStatusMap, vplexIngestionMethod,
                volume);
        
        // Decorate volume with RP Properties.
        decorateVolumeWithRPProperties(volume, unManagedVolume);

        // Update the unmanaged protection set
        decorateUnManagedProtectionSet(umpset, volume, unManagedVolume);

        // Perform RP-specific export ingestion
        performRPExportIngestion(project, virtualArray, vPool, unManagedVolume, volume); 

        // Print post-ingestion report
        _logger.info("Printing Ingestion Report After Ingestion");
        _logger.info(getRPIngestionStatus(umpset));

        // Create the managed protection set/CG objects when we have all of the volumes ingested
        if (validateAllVolumesInCGIngested(unManagedVolume, umpset)) {
            _logger.info("Successfully ingested all volumes associated with RP consistency group");
            ProtectionSet pset = createProtectionSet(umpset);
            BlockConsistencyGroup bcg = createBlockConsistencyGroup(pset);
            
            // Once we have a proper managed consistency group and protection set, we need to 
            // sprinkle those references over the managed volumes.
            decorateVolumeInformationFinalIngest(pset, bcg);
            
            // Delete the unmanaged protection set
            _dbClient.removeObject(umpset);
        }
        
        return clazz.cast(volume);
    }

    /**
     * Perform RP volume ingestion.  Typically this involves finding the proper ingestion orchestrator
     * for the volume type (minus the fact it's RP, which got us to this code in the first place), then
     * calling block ingest on that orchestrator.
     * 
     * @param project project
     * @param virtualArray virtual array
     * @param vPool virtual pool
     * @param system storage system
     * @param unManagedVolume unmanaged volume we're ingesting
     * @param systemCache storage system URI cache
     * @param poolCache storage pool URI cache
     * @param tenant tenant info
     * @param unManagedVolumesSuccessfullyProcessed list of volumes that are inactive after ingest
     * @param createdObjectMap map of created block objects
     * @param updatedObjectMap map of updated block objects
     * @param taskStatusMap map of block object task statuses
     * @param vplexIngestionMethod N/A
     * @param volume resulting ingested volume
     * @return volume that is ingested
     */
    @SuppressWarnings("unchecked")
    private Volume performRPVolumeIngestion(Project project, VirtualArray virtualArray, VirtualPool vPool, StorageSystem system,
            UnManagedVolume unManagedVolume, List<URI> systemCache, List<URI> poolCache, TenantOrg tenant,
            List<UnManagedVolume> unManagedVolumesSuccessfullyProcessed, Map<String, BlockObject> createdObjectMap,
            Map<String, List<DataObject>> updatedObjectMap, Map<String, StringBuffer> taskStatusMap, String vplexIngestionMethod,
            Volume volume) {
        if (null == volume) {
            // TODO: This area of the code is an example of where a transactional boundary would be wonderful.
            // If we ingest this block volume, but then have a failure in export ingestion later, we end up in a state
            // where the volume is ingested, but its export isn't.  We need to figure out a way to keep all these 
            // objects around in their pre-ingestion state in case of failure.
            //
            // We check above to see if the volume is ingested already, but that only helps us if the previous ingestion
            // got partially done.  If it got done all the way, the unmanaged volume was deleted, and a future request to 
            // ingest that unmanaged volume ID will fail.

            // We need to ingest the volume w/o the context of RP.  (So, ingest a VMAX if it's VMAX, VPLEX if it's VPLEX, etc)
            IngestStrategy ingestStrategy = ingestStrategyFactory.buildIngestStrategy(unManagedVolume, true);
            volume = (Volume)ingestStrategy.ingestBlockObjects(systemCache, poolCache, system, unManagedVolume, vPool, virtualArray,
                    project, tenant, unManagedVolumesSuccessfullyProcessed, createdObjectMap, updatedObjectMap, true /* force true exported field */,
                    VolumeIngestionUtil.getBlockObjectClass(unManagedVolume), taskStatusMap, vplexIngestionMethod);
            _logger.info("Ingestion ended for unmanagedvolume {}", unManagedVolume.getNativeGuid());
            if (null == volume) {
                throw IngestionException.exceptions.generalVolumeException(
                        unManagedVolume.getLabel(), "check the logs for more details");
            }
        } else {
            // blockObject already ingested, now just update internalflags &
            // RP relationships. Run this logic always when volume NO_PUBLIC_ACCESS
            if (markUnManagedVolumeInactive(unManagedVolume, volume, unManagedVolumesSuccessfullyProcessed,
                    createdObjectMap, updatedObjectMap, taskStatusMap, null)) {
                _logger.info("All the related replicas and parent of unManagedVolume {} has been ingested ",
                        unManagedVolume.getNativeGuid());
                unManagedVolume.setInactive(true);
                unManagedVolumesSuccessfullyProcessed.add(unManagedVolume);
            } else {
                _logger.info(
                        "Not all the parent/replicas of unManagedVolume {} have been ingested , hence marking as internal",
                        unManagedVolume.getNativeGuid());
                volume.addInternalFlags(INTERNAL_VOLUME_FLAGS);
            }
        }
        return volume;
    }

    /**
     * Decorates the block objects with RP properties.  Also updates the unmanaged volume object with
     * any references needed for future ingestions of RP volumes.
     * 
     * @param volume volume that is the result of the ingest
     * @param unManagedVolume unmanaged volume with RP properties (VolumeInformation) on it
     */
    private void decorateVolumeWithRPProperties(Volume volume, UnManagedVolume unManagedVolume) {
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        String type = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.RP_PERSONALITY.toString(), unManagedVolumeInformation);
        if (Volume.PersonalityTypes.SOURCE.toString().equalsIgnoreCase(type)) {
            decorateUpdatesForRPSource(volume, unManagedVolume);
        } else if (Volume.PersonalityTypes.TARGET.toString().equalsIgnoreCase(type)) {
            decorateUpdatesForRPTarget(volume, unManagedVolume);
        } else if (Volume.PersonalityTypes.METADATA.toString().equalsIgnoreCase(type)) {
            volume.setPersonality(PersonalityTypes.METADATA.toString());
            volume.setAccessState(Volume.VolumeAccessState.NOT_READY.toString());
        }
        
        // Set the various RP related fields
        String rpCopyName = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.RP_COPY_NAME.toString(), unManagedVolumeInformation);
        String rpRSetName = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.RP_RSET_NAME.toString(), unManagedVolumeInformation);
        String rpProtectionSystem = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.RP_PROTECTIONSYSTEM.toString(), unManagedVolumeInformation);
        String rpInternalSiteName = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.RP_INTERNAL_SITENAME.toString(), unManagedVolumeInformation);
        
        volume.setRpCopyName(rpCopyName); // This comes from UNMANAGED_CG discovery of Protection System
        volume.setRSetName(rpRSetName); // This comes from UNMANAGED_CG discovery of Protection System
        volume.setInternalSiteName(rpInternalSiteName); // This comes from UNMANAGED_CG discovery of Protection System
        volume.setProtectionController(URI.create(rpProtectionSystem)); // This comes from UNMANAGED_CG discovery of Protection System
        if (null != _dbClient.queryObject(Volume.class, volume.getId())) {
            _dbClient.updateAndReindexObject(volume);
        } else {
            _dbClient.createObject(volume);
        }
    }

    /**
     * Perform updates of the managed volume and associated unmanaged volumes and protection sets
     * given an RP source volume getting ingested.
     * 
     * @param volume managed volume
     * @param unManagedVolume unmanaged volume
     */
    private void decorateUpdatesForRPSource(Volume volume, UnManagedVolume unManagedVolume) {
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        volume.setPersonality(PersonalityTypes.SOURCE.toString());
        volume.setAccessState(Volume.VolumeAccessState.READWRITE.toString());
        volume.setLinkStatus(Volume.LinkStatus.IN_SYNC.toString());
        
        // When we ingest a source volume, we need to properly create the RP Target list for that source,
        // however it is possible that not all (or any) of the RP targets have been ingested yet.  Therefore
        // we need to do as much as we can:
        // 
        // 1. Process each managed target volume ID in the unmanaged source volume, add to the managed source volume's RP target list.
        // 2. Go through each unmanaged RP target volume in the unmanaged source volume (before it goes away), add the managed source volume ID.
        // 3. Go through each unmanaged RP target volume in the unmanaged source volume, remove the unmanaged source volume ID.
        
        // 1. Process each managed target volume ID in the unmanaged source volume, add to the managed source volume's RP target list.
        StringSet rpManagedTargetVolumeIdStrs = PropertySetterUtil.extractValuesFromStringSet(SupportedVolumeInformation.RP_MANAGED_TARGET_VOLUMES.toString(), 
                unManagedVolumeInformation);
        for (String rpManagedTargetVolumeIdStr : rpManagedTargetVolumeIdStrs) {
            // Check to make sure the target volume is legit.
            Volume managedTargetVolume = _dbClient.queryObject(Volume.class, URI.create(rpManagedTargetVolumeIdStr));
            if (managedTargetVolume == null) {
                _logger.error("Could not find managed target volume: " + rpManagedTargetVolumeIdStr + " in DB.  Ingestion failed.");
                throw IngestionException.exceptions.noManagedTargetVolumeFound(unManagedVolume.getNativeGuid(), rpManagedTargetVolumeIdStr);
            }
            
            if (volume.getRpTargets() == null) {
                volume.setRpTargets(new StringSet());
            }
            volume.getRpTargets().add(managedTargetVolume.getId().toString());
        }

        // 2. Go through each unmanaged RP target volume in the unmanaged source volume (before it goes away), add the managed source volume ID.
        // 3. Go through each unmanaged RP target volume in the unmanaged source volume, remove the unmanaged source volume ID.
        StringSet rpUnManagedTargetVolumeIdStrs = PropertySetterUtil.extractValuesFromStringSet(SupportedVolumeInformation.RP_UNMANAGED_TARGET_VOLUMES.toString(), 
                unManagedVolumeInformation);
        for (String rpUnManagedTargetVolumeIdStr : rpUnManagedTargetVolumeIdStrs) {
            UnManagedVolume unManagedTargetVolume = _dbClient.queryObject(UnManagedVolume.class, URI.create(rpUnManagedTargetVolumeIdStr));
            if (unManagedTargetVolume == null) {
                _logger.error("Could not find unmanaged target volume: " + rpUnManagedTargetVolumeIdStr + " in DB.  Ingestion failed.");
                throw IngestionException.exceptions.noUnManagedTargetVolumeFound(unManagedVolume.getNativeGuid(), rpUnManagedTargetVolumeIdStr);
            }

            // (2) Add the managed source volume ID to this target that hasn't been ingested yet, so when it IS ingested, we know
            //     what RP source it belongs to.
            StringSet rpManagedSourceVolumeId = new StringSet();
            rpManagedSourceVolumeId.add(volume.getId().toString());
            unManagedTargetVolume.putVolumeInfo(SupportedVolumeInformation.RP_MANAGED_SOURCE_VOLUME.toString(),
                    rpManagedSourceVolumeId);

            // (3) Remove the unmanaged source volume ID to this target that is going away as a result of ingestion.
            //     This is for completeness.  The ID is going away in the DB, so we don't want any references to it anywhere.
            StringSet rpUnManagedSourceVolumeId = new StringSet();
            unManagedTargetVolume.putVolumeInfo(SupportedVolumeInformation.RP_UNMANAGED_SOURCE_VOLUME.toString(),
                    rpUnManagedSourceVolumeId);
            
            _dbClient.updateAndReindexObject(unManagedTargetVolume);
        }
    }

    /**
     * Perform updates of the managed volume and associated unmanaged volumes and protection sets
     * given an RP target volume getting ingested.
     * 
     * @param volume managed volume
     * @param unManagedVolume unmanaged volume
     */
    private void decorateUpdatesForRPTarget(Volume volume, UnManagedVolume unManagedVolume) {
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        volume.setPersonality(PersonalityTypes.TARGET.toString());
        volume.setAccessState(Volume.VolumeAccessState.NOT_READY.toString());
        volume.setLinkStatus(Volume.LinkStatus.IN_SYNC.toString());
        
        // Any time a target goes from UnManaged -> Managed, we need to ensure that:
        // 1. If there is a source managed volume, it gets the managed target volume added to its RP Target List
        // 2. If there is a source Unmanaged volume, the managed target volume added to its RP_MANAGED_TARGET_VOLUMES list
        // 3. If there is a source Unmanaged volume, the unmanaged target volume is removed from the RP_UNMANAGED_TARGET_VOLUMES list
        // 
        // This ensures that we don't lose track of sources and targets, regardless of the order volumes are ingested and unmanaged volumes are
        // deleted during the ingestion process.
        
        // First check to see if there's a managed volume out there with this blockObject's ID in its RP target list.
        
        // Add this target volume to the RP source's target list
        String rpManagedSourceVolume = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.RP_MANAGED_SOURCE_VOLUME.toString(), unManagedVolumeInformation);
        if (rpManagedSourceVolume != null) {
            // (1) Add the new managed target volume ID to the source volume's RP target list
            Volume sourceVolume = _dbClient.queryObject(Volume.class, URI.create(rpManagedSourceVolume));
            if (sourceVolume == null) {
                _logger.error("Could not find managed RP source volume in DB: " + rpManagedSourceVolume);
                throw IngestionException.exceptions.noManagedSourceVolumeFound(unManagedVolume.getNativeGuid(), rpManagedSourceVolume);
            }
            
            if (sourceVolume.getRpTargets() == null) {
                sourceVolume.setRpTargets(new StringSet());
            }
            sourceVolume.getRpTargets().add(volume.getId().toString());
            _dbClient.updateAndReindexObject(sourceVolume);
        } else {
            _logger.info("There is no ingested RP source volume associated with this target yet: " + volume.getLabel());

            String rpUnManagedSourceVolume = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.RP_UNMANAGED_SOURCE_VOLUME.toString(), unManagedVolumeInformation);
            if (rpUnManagedSourceVolume == null) {
                _logger.error("There is no uningested RP source volume associated with this target either.  This is an error condition: " + volume.getLabel());
                throw IngestionException.exceptions.noUnManagedSourceVolumeFound(unManagedVolume.getNativeGuid());
            }
            
            // (2) Add the managed target to the RP_MANAGED_TARGET_VOLUMES list associated with the unmanaged source volume
            UnManagedVolume unManagedSourceVolume = _dbClient.queryObject(UnManagedVolume.class, URI.create(rpUnManagedSourceVolume));
            if (unManagedSourceVolume == null) {
                _logger.error("Could not find unmanaged RP source volume in DB: " + rpUnManagedSourceVolume);
                throw IngestionException.exceptions.noUnManagedSourceVolumeFound2(unManagedVolume.getNativeGuid(), rpUnManagedSourceVolume);
            }
            
            StringSet rpManagedTargetVolumeIdStrs = PropertySetterUtil.extractValuesFromStringSet(SupportedVolumeInformation.RP_MANAGED_TARGET_VOLUMES.toString(), 
                    unManagedSourceVolume.getVolumeInformation());
            rpManagedTargetVolumeIdStrs.add(volume.getId().toString());
            unManagedSourceVolume.putVolumeInfo(SupportedVolumeInformation.RP_MANAGED_TARGET_VOLUMES.toString(),
                    rpManagedTargetVolumeIdStrs);
            
            // (3) Remove the unmanaged target from the RP_UNMANAGED_TARGET_VOLUMES list associated with the unmanaged source volume
            StringSet rpUnManagedTargetVolumeIdStrs = PropertySetterUtil.extractValuesFromStringSet(SupportedVolumeInformation.RP_UNMANAGED_TARGET_VOLUMES.toString(), 
                    unManagedSourceVolume.getVolumeInformation());
            rpUnManagedTargetVolumeIdStrs.remove(unManagedVolume.getId().toString());
            unManagedSourceVolume.putVolumeInfo(SupportedVolumeInformation.RP_UNMANAGED_TARGET_VOLUMES.toString(),
                    rpUnManagedTargetVolumeIdStrs);
            
            _dbClient.updateAndReindexObject(unManagedSourceVolume);
        }
    }

    /**
     * The unmanaged protection is responsible for keeping track of the managed and unmanaged volumes that 
     * are associated with the RP CG.  This method keeps those managed and unmanaged IDs up to date.
     * 
     * @param umpset unmanaged protection set to update
     * @param volume the managed volume
     * @param unManagedVolume the unmanaged volume
     */
    private void decorateUnManagedProtectionSet(UnManagedProtectionSet umpset, Volume volume, UnManagedVolume unManagedVolume) {
        // Add the volume to the list of managed volumes we have so far.
        if (!umpset.getManagedVolumeIds().contains(volume.getId().toString())) {
            umpset.getManagedVolumeIds().add(volume.getId().toString());
        }
        
        // Remove the unmanaged volume from the list we have so far since that is going inactive.
        if (umpset.getUnManagedVolumeIds().contains(unManagedVolume.getId().toString())) {
            umpset.getUnManagedVolumeIds().remove(unManagedVolume.getId().toString());
        }

        // Update the unmanaged protection set object
        _dbClient.persistObject(umpset);
    }
    
    /**
     * This method will perform all of the final decorations (attribute setting) on the Volume
     * object after creating the required BlockConsistencyGroup and ProtectionSet objects.
     * 
     * Fields such as rpCopyName and rSetName were already filled in when we did the ingest of 
     * the volume itself.  In this method, we worry about stitching together all of the object
     * references within the Volume object so it will act like a native CoprHD-created RP volume.
     * 
     * @param pset protection set
     * @param cg block consistency group
     */
    private void decorateVolumeInformationFinalIngest(ProtectionSet pset, BlockConsistencyGroup cg) {
        if (pset.getVolumes() == null) {
            _logger.error("No volumes found in protection set: " + pset.getLabel() + ", cannot process ingestion");
            throw IngestionException.exceptions.noVolumesFoundInProtectionSet(pset.getLabel());
        }
        
        // Set references to protection set/CGs properly in each volume
        List<Volume> volumes = _dbClient.queryObject(Volume.class, URIUtil.toURIList(pset.getVolumes()));

        for (Volume volume : volumes) {
            volume.setConsistencyGroup(cg.getId());
            volume.setProtectionSet(new NamedURI(pset.getId(), pset.getLabel()));

            // For sources and targets, peg an RP journal volume to be associated with each.
            // This is a bit arbitrary for ingested RP volues as they may have 5 journal volumes for one source volume.
            // We just pick one since we only store one journal volume ID in a Volume object.
            if (volume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.SOURCE.toString()) ||
                    volume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.TARGET.toString())) {
                // Find a journal for that rp copy
                for (Volume journalVolume : volumes) {
                    if (journalVolume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.METADATA.toString()) &&
                            journalVolume.getRpCopyName() != null &&
                            volume.getRpCopyName() != null &&
                            journalVolume.getRpCopyName().equals(volume.getRpCopyName())) {
                        volume.setRpJournalVolume(journalVolume.getId());
                    }
                }
            }
            
            _dbClient.persistObject(volume);
        }
    }

    /**
     * RecoverPoint volumes are expected to have export masks where the volume is exported to 
     * a RecoverPoint site.  Therefore every RP volume (sources, targets, journals) will need to 
     * go through this code and have their export mask ingested.  Even if the mask has already been
     * ingested by a previous volume ingestion, this method still needs to update the ExportGroup and
     * ExportMask objects to reflect the newly ingested volume as part of its management.
     * 
     * @param project project
     * @param virtualArray virtual array
     * @param vPool virtual pool (not used for pathing parameters, RP has its own rules for them) 
     * @param unManagedVolume unmanaged volume
     * @param volume managed volume
     * @return managed volume with export ingested
     */
    private void performRPExportIngestion(Project project, VirtualArray virtualArray, VirtualPool vPool,
            UnManagedVolume unManagedVolume, Volume volume) {

        // TODO: In the case where the source or target is exported to a host as well, VMAX2 best practices dictate that you
        // create separate MVs for each host's RP volumes.  That would mean a different export group per host/cluster.
        ProtectionSystem protectionSystem = _dbClient.queryObject(ProtectionSystem.class, volume.getProtectionController());
        UnManagedExportMask em = findUnManagedRPExportMask(protectionSystem, unManagedVolume);
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        
        if (em == null) {
            _logger.error("Could not find an unmanaged export mask associated with volume: " + unManagedVolume.getLabel());
            throw IngestionException.exceptions.noUnManagedExportMaskFound(unManagedVolume.getNativeGuid());
        }
        
        ExportGroup exportGroup = VolumeIngestionUtil.verifyExportGroupExists(project.getId(), em.getKnownInitiatorUris(),
                virtualArray.getId(), _dbClient);
        boolean newExportGroupWasCreated = false;
        if (null == exportGroup) {
            newExportGroupWasCreated = true;
            _logger.info("Creating Export Group with label {}", em.getMaskName());
            // No existing group has the mask, let's create one.
            exportGroup = new ExportGroup();
            exportGroup.setId(URIUtil.createId(ExportGroup.class));
            exportGroup.addInternalFlags(Flag.INTERNAL_OBJECT, Flag.SUPPORTS_FORCE, Flag.RECOVERPOINT);
            exportGroup.setProject(new NamedURI(project.getId(), project.getLabel()));
            exportGroup.setVirtualArray(virtualArray.getId());
            exportGroup.setTenant(new NamedURI(project.getTenantOrg().getURI(), project.getTenantOrg().getName()));
            String exportGroupGeneratedName = protectionSystem.getNativeGuid() + "_" + storageSystem.getLabel() + "_" + volume.getInternalSiteName() + "_"
                    + virtualArray.getLabel();
            // Remove all non alpha-numeric characters, excluding "_".
            exportGroupGeneratedName = exportGroupGeneratedName.replaceAll("[^A-Za-z0-9_]", "");
            exportGroup.setGeneratedName(exportGroupGeneratedName);
            // When created by CoprHD natively, it's usually the CG name.
            exportGroup.setLabel(exportGroupGeneratedName);
            exportGroup.setVolumes(new StringMap());
            exportGroup.setOpStatus(new OpStatusMap());
            // TODO: May need to use a default size or compute based on the contents of the export mask.
            exportGroup.setNumPaths(em.getZoningMap().size());
            exportGroup.setZoneAllInitiators(true);
        }
        
        // create an ingest param so that we can reuse the ingestExportMask method
        VolumeExportIngestParam exportIngestParam = new VolumeExportIngestParam();
        exportIngestParam.setProject(project.getId());
        exportIngestParam.setVarray(virtualArray.getId());
        exportIngestParam.setVpool(vPool.getId());
        List<URI> volumeUris = new ArrayList<URI>();
        volumeUris.add(unManagedVolume.getId());
        exportIngestParam.setUnManagedVolumes(volumeUris);

        // find the ingest export strategy and call into for this unmanaged export mask
        IngestExportStrategy ingestStrategy = ingestStrategyFactory.buildIngestExportStrategy(unManagedVolume);
        List<UnManagedVolume> unManagedVolumesToBeDeleted = new ArrayList<UnManagedVolume>();
        volume = ingestStrategy.ingestExportMasks(
                unManagedVolume, exportIngestParam, exportGroup,
                volume, unManagedVolumesToBeDeleted,
                _dbClient.queryObject(StorageSystem.class, volume.getStorageController()), 
                newExportGroupWasCreated, _dbClient.queryObject(Initiator.class, URIUtil.toURIList(em.getKnownInitiatorUris())));

        if (null == volume) {
            // an exception should have been thrown by a lower layer in
            // ingestion did not succeed, but in case it wasn't, throw one
            throw IngestionException.exceptions.generalVolumeException(
                    unManagedVolume.getLabel(), "check the logs for more details");
        }
    }

    /**
     * This unmanaged volume may be associated with several export masks.  We need to find the export mask
     * that belongs specifically to the RP protection system supplied.
     * 
     * Note: There should only be one (1) mask that contains both the protection system's initiators AND the volume.
     *       If this is not true, this method (and its caller) need to be reconsidered.
     *       
     * @param protectionSystem protection system
     * @param unManagedVolume unmanaged volume
     * @return unmanaged export mask that belongs to the protection system that contains the unmanaged volume
     */
    private UnManagedExportMask findUnManagedRPExportMask(ProtectionSystem protectionSystem, UnManagedVolume unManagedVolume) {
        UnManagedExportMask em = null;
        for (String maskIdStr : unManagedVolume.getUnmanagedExportMasks()) {

            // Find the mask associated with the protection system.  (Assume there's only one for this volume)
            em = _dbClient.queryObject(UnManagedExportMask.class, URI.create(maskIdStr));
            if (em == null) {
                _logger.error("UnManagedExportMask with ID: " + maskIdStr + " could not be found in DB.  Could already be ingested.");
                continue;
            }
            
            // Check for unlikely conditions on the mask, such as no initiators assigned.
            if (em.getKnownInitiatorNetworkIds() == null || em.getKnownInitiatorNetworkIds().isEmpty()) {
                _logger.error("UnManagedExportMask with ID: " + maskIdStr + " does not contain any RP initiators.  Ignoring for ingestion.");
                continue;
            }
            
            for (String wwn : em.getKnownInitiatorNetworkIds()) {
                for (Entry<String, AbstractChangeTrackingSet<String>> siteInitEntry : protectionSystem.getSiteInitiators().entrySet()) {
                    if (siteInitEntry.getValue().contains(wwn)) {
                        _logger.info(String.format("Found UnManagedVolume %s was found in UnManagedExportMask %s and will be ingested (if not ingested already)", unManagedVolume.getLabel(), em.getMaskName()));
                        return em;
                    }
                }
            }
        }
        
        // The caller will throw the exception
        return null;
    }

    /**
     * Check to see if all of the volumes associated with the RP CG are now ingested.
     * 
     * @param unManagedVolume unmanaged volume object
     * 
     * @return true if all volumes in CG are ingested
     */
    private boolean validateAllVolumesInCGIngested(UnManagedVolume unManagedVolume, UnManagedProtectionSet umpset) {
        if (umpset == null) {
            _logger.error("Unable to find unmanaged protection set associated with volume: " + unManagedVolume.getId() + " Please run unmanaged CG discovery of registered protection systems");
            throw IngestionException.exceptions.unManagedProtectionSetNotFound(unManagedVolume.getNativeGuid());
        }
        
        // Make sure the managed volumes match the unmanaged volumes and WWN list
        if (umpset.getUnManagedVolumeIds() != null && umpset.getManagedVolumeIds() != null && umpset.getVolumeWwns() != null &&
                umpset.getUnManagedVolumeIds().size() == umpset.getManagedVolumeIds().size() &&
                umpset.getManagedVolumeIds().size() == umpset.getVolumeWwns().size()) {
            _logger.info("Found that all volumes associated with the RP CG have been ingested: " + umpset.getCgName());
            return true;
        }

        // Extremely unlikely #1: No unmanaged volume IDs in the protection set.  We wouldn't have stored the unmanaged protection set in this case.
        if (umpset.getUnManagedVolumeIds() == null) {
            String msg = String.format("INGEST VALIDATION: No unmanaged volumes found in unmanaged protection set: " + umpset.getCgName());                    
            _logger.error(msg);
            return false;
        }
        
        // Extremely unlikely #2: Every ingest operation puts a volume in this list.
        if (umpset.getManagedVolumeIds() == null) {
            String msg = String.format("INGEST VALIDATION: No managed volumes found in unmanaged protection set: " + umpset.getCgName());                    
            _logger.error(msg);
            return false;
        }

        // Extremely unlikely #3: See #1.  We would not have created the protection set if there weren't volumes.
        if (umpset.getVolumeWwns() == null) {
            String msg = String.format("INGEST VALIDATION: No volume WWNs found in unmanaged protection set: " + umpset.getCgName());                    
            _logger.error(msg);
            return false;
        }
        
        // Very likely: We haven't quite ingested everything yet.
        if (!umpset.getUnManagedVolumeIds().isEmpty()) {
            String msg = String.format("INGEST VALIDATION: Found that the unmanaged protection set: %s is not yet ingestable because there " +
                    "are %d volumes to be ingested, however only %d volume have been ingested.", umpset.getCgName(), umpset.getVolumeWwns().size(), umpset.getManagedVolumeIds().size());
            _logger.info(msg);
            // TODO: Iterate over the unmanaged volumes that we haven't ingested yet and print them up.
            return false;
        } 
        
        if (umpset.getManagedVolumeIds().size() != umpset.getVolumeWwns().size()) {
            String msg = String.format("INGEST VALIDATION: Found that the unmanaged protection set: %s is not yet ingestable because there " +
                    " are %d volumes in the RP CG that are on arrays that are not under management.", umpset.getCgName(), umpset.getVolumeWwns().size()-umpset.getManagedVolumeIds().size());
            _logger.info(msg);
            // TODO: Iterate over the volume WWNs (maybe the array serial number?) that aren't in our management.
            return false;
        }

        _logger.info("INGEST VALIDATION: All of the volumes associated with RP CG " + umpset.getCgName() + " have been ingested.");
        return true;
    }

    /**
     * Find the unmanaged protection set associated with the unmanaged volume
     * 
     * @param unManagedVolume unmanaged volume
     * @return unmanaged protection set associated with that volume
     */
    private UnManagedProtectionSet getUnManagedProtectionSet(UnManagedVolume unManagedVolume) {
        // Find the UnManagedProtectionSet associated with this unmanaged volume
        List<UnManagedProtectionSet> umpsets = CustomQueryUtility.getUnManagedProtectionSetByUnManagedVolumeId(_dbClient, unManagedVolume.getId().toString());
        Iterator<UnManagedProtectionSet> umpsetsItr = umpsets.iterator();
        if (!umpsetsItr.hasNext()) {
            _logger.error("Unable to find unmanaged protection set associated with volume: " + unManagedVolume.getId());
            // caller will throw exception
            return null;
        }
        
        return umpsetsItr.next();
    }
    
    /**
     * Create the managed protection set associated with the ingested RP volumes.
     * Also, as a side-effect, insert the protection set ID into each of the impacted volumes.
     * 
     * @param umpset unmanaged protection set (cg)
     * @return a new protection set object
     */
    private ProtectionSet createProtectionSet(UnManagedProtectionSet umpset) {
        StringSetMap unManagedCGInformation = umpset.getCGInformation();
        String rpProtectionId = PropertySetterUtil.extractValueFromStringSet(
                SupportedCGInformation.PROTECTION_ID.toString(), unManagedCGInformation);

        ProtectionSet pset = new ProtectionSet();
        pset.setId(URIUtil.createId(ProtectionSet.class));
        pset.setLabel(umpset.getCgName());
        pset.setProtectionId(rpProtectionId);
        pset.setProtectionStatus(ProtectionStatus.ENABLED.toString());
        pset.setProtectionSystem(umpset.getProtectionSystemUri());

        if (umpset.getManagedVolumeIds() != null) {
            for (String volumeID : umpset.getManagedVolumeIds()) {
                Volume volume = _dbClient.queryObject(Volume.class, URI.create(volumeID));
                if (volume == null) {
                    _logger.error("Unable to retrieve volume : " + volume + " from database.  Ignoring in protection set ingestion.");
                    continue;
                }

                // Set the project value
                if (pset.getProject() == null) {
                    pset.setProject(volume.getProject().getURI());
                }

                // Add all volumes (managed only) to the new protection set
                if (pset.getVolumes() == null) {
                    pset.setVolumes(new StringSet());
                }

                pset.getVolumes().add(volumeID);
            }         
        }

        _logger.info("Created new protection set: " + pset.getId().toString());
        _dbClient.createObject(pset);
        return pset;
    }
    
    /**
     * Create the block consistency group object associated with the CG as part of ingestion.
     * 
     * @param pset protection set object
     * @return a new block consistency group object
     */
    private BlockConsistencyGroup createBlockConsistencyGroup(ProtectionSet pset) {
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        cg.setId(URIUtil.createId(BlockConsistencyGroup.class));
        cg.setLabel(pset.getLabel());
        Project project = _dbClient.queryObject(Project.class, pset.getProject());
        cg.setProject(new NamedURI(pset.getProject(), project.getLabel()));
        StringSet types = new StringSet();
        types.add(BlockConsistencyGroup.Types.RP.toString());
        cg.setRequestedTypes(types);
        cg.setTypes(types);
        cg.setTenant(project.getTenantOrg());
        cg.addSystemConsistencyGroup(pset.getProtectionSystem().toString(), pset.getLabel());
        _logger.info("Created new block consistency group: " + cg.getId().toString());
        _dbClient.createObject(cg);
        return cg;
    }
    
    /**
     * Validates the UnManagedVolume Properties to make sure it has everything needed to be ingested.
     * 
     * @param unManagedVolume unmanaged volume 
     * @param virtualArray virtual array 
     * @param virtualPool virtual pool
     * @param project project
     */
    private void validateUnManagedVolumeProperties(UnManagedVolume unManagedVolume, VirtualArray virtualArray,
            VirtualPool virtualPool, Project project) {
        // TODO: Fill this in  
        // For example, you could put a check in here that ensures that the TARGET/METADATA are associated
        // with some RP vpool.  It would be good to fail the ingestion early with an error that says "You're
        // trying to ingest this target/journal volume in a vpool that is not associated with a RP vpool."       
    }

    private static enum ColumnEnum {
        NAME(0), ID(1), PERSONALITY(2), COPY_NAME(3), RSET_NAME(4), VARRAY(5), VPOOL(6);

        private int column;

        private static Map<Integer, ColumnEnum> map = new HashMap<Integer, ColumnEnum>();

        static {
            for (ColumnEnum columnEnum : ColumnEnum.values()) {
                map.put(columnEnum.column, columnEnum);
            }
        }

        private ColumnEnum(final int column) { this.column = column; }

        public static ColumnEnum valueOf(int column) {
            return map.get(column);
        }
        
        public int getColumnNum() {
            return column;
        }
    }

    /**
     * This method will assemble a status printout of the ingestion progress for this protection set.
     * 
     * @param umpset unmanaged protection set
     * @return String status (multi-line, formatted)
     */
    private String getRPIngestionStatus(UnManagedProtectionSet umpset) {
        StringBuffer sb = new StringBuffer();
        
        sb.append("\nRecoverPoint Ingestion Progress Report\n");
        sb.append(  "--------------------------------------\n");
        
        sb.append(    "RP CG Name:        " + umpset.getCgName() + "\n");
        if (umpset.getProtectionSystemUri() != null) {
            ProtectionSystem ps = _dbClient.queryObject(ProtectionSystem.class, umpset.getProtectionSystemUri());
            sb.append("Protection System: " + ps.getLabel() + " [" + ps.getIpAddress() + "]" );
        }

        // Keep track of the column widths
        Map<Integer, Integer> columnWidthMap = new HashMap<Integer, Integer>();
        for (int column = 0; column < ColumnEnum.map.keySet().size() ; column++) {
            columnWidthMap.put(column, ColumnEnum.valueOf(column).toString().length());
        }
        
        if (!umpset.getManagedVolumeIds().isEmpty()) {
            sb.append("\n\nIngested Volumes:\n");
            List<Volume> volumes = _dbClient.queryObject(Volume.class, URIUtil.toURIList(umpset.getManagedVolumeIds()));
            for (Volume volume : volumes) {
                // Get the width of the columns so we can have a compact, formatted printout.
                // Name, ID, Personality, copy name, rset name, varray name, vpool name
                if (volume.getLabel() != null && columnWidthMap.get(ColumnEnum.NAME.getColumnNum()) < volume.getLabel().length()) {
                    columnWidthMap.put(ColumnEnum.NAME.getColumnNum(), volume.getLabel().length());
                }
                if (volume.getId() != null && columnWidthMap.get(ColumnEnum.ID.getColumnNum()) < volume.getId().toString().length()) {
                    columnWidthMap.put(ColumnEnum.ID.getColumnNum(), volume.getId().toString().length());
                }
                if (volume.getPersonality() != null && columnWidthMap.get(ColumnEnum.PERSONALITY.getColumnNum()) < volume.getPersonality().length()) {
                    columnWidthMap.put(ColumnEnum.PERSONALITY.getColumnNum(), volume.getPersonality().length());
                }
                if (volume.getRpCopyName() != null && columnWidthMap.get(ColumnEnum.COPY_NAME.getColumnNum()) < volume.getRpCopyName().length()) {
                    columnWidthMap.put(ColumnEnum.COPY_NAME.getColumnNum(), volume.getRpCopyName().length());
                }
                if (volume.getRSetName() != null && columnWidthMap.get(ColumnEnum.RSET_NAME.getColumnNum()) < volume.getRSetName().length()) {
                    columnWidthMap.put(ColumnEnum.RSET_NAME.getColumnNum(), volume.getRSetName().length());
                }
                if (volume.getVirtualArray() != null) {
                    VirtualArray vArray = _dbClient.queryObject(VirtualArray.class, volume.getVirtualArray());
                    if (vArray != null && vArray.getLabel() != null && columnWidthMap.get(ColumnEnum.RSET_NAME.getColumnNum()) < vArray.getLabel().length()) {
                        columnWidthMap.put(ColumnEnum.VARRAY.getColumnNum(), vArray.getLabel().length());
                    }
                }
                if (volume.getVirtualPool() != null) {
                    VirtualPool vPool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
                    if (vPool != null && vPool.getLabel() != null && columnWidthMap.get(ColumnEnum.RSET_NAME.getColumnNum()) < vPool.getLabel().length()) {
                        columnWidthMap.put(ColumnEnum.VPOOL.getColumnNum(), vPool.getLabel().length());
                    }
                }
            }

            StringBuffer widthFormat = new StringBuffer();
            for (int column = 0; column < ColumnEnum.map.keySet().size() ; column++) {
                StringBuffer formatBuf = new StringBuffer();
                formatBuf.append("%");
                formatBuf.append(String.format("%d", columnWidthMap.get(column)));
                formatBuf.append("s ");
                sb.append(String.format(formatBuf.toString(), ColumnEnum.valueOf(column).name()));
                widthFormat.append(formatBuf.toString());
            }
            
            sb.append("\n");
            widthFormat.append("\n");
            
            // Now actually print the values
            for (Volume volume : volumes) {
                String vArrayLabel = "N/A";
                String vPoolLabel = "N/A";

                if (volume.getVirtualArray() != null) {
                    VirtualArray vArray = _dbClient.queryObject(VirtualArray.class, volume.getVirtualArray());
                    vArrayLabel = vArray.getLabel();
                }
                
                if (volume.getVirtualPool() != null) {
                    VirtualPool vPool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
                    vPoolLabel = vPool.getLabel();
                }
                
                sb.append(String.format(widthFormat.toString(), 
                        volume.getLabel() != null ? volume.getLabel() : "N/A",
                        volume.getId() != null ? volume.getId() : "N/A",
                        volume.getPersonality() != null ? volume.getPersonality() : "N/A",
                        volume.getRpCopyName() != null ? volume.getRpCopyName() : "N/A",
                        volume.getRSetName() != null ? volume.getRSetName() : "N/A",
                        vArrayLabel,
                        vPoolLabel));
            }
        }

        // Keep track of the column widths
        columnWidthMap.clear();
        for (int column = 0; column < ColumnEnum.map.keySet().size() ; column++) {
            columnWidthMap.put(column, ColumnEnum.valueOf(column).toString().length());
        }
        
        if (!umpset.getUnManagedVolumeIds().isEmpty()) {
            sb.append("\nUningested Volumes:\n");
            List<UnManagedVolume> volumes = _dbClient.queryObject(UnManagedVolume.class, URIUtil.toURIList(umpset.getUnManagedVolumeIds()));
            for (UnManagedVolume volume : volumes) {
                // Get the width of the columns so we can have a compact, formatted printout.
                // Name, ID, Personality, copy name, rset name, varray name, vpool name
                if (volume.getLabel() != null && columnWidthMap.get(ColumnEnum.NAME.getColumnNum()) < volume.getLabel().length()) {
                    columnWidthMap.put(ColumnEnum.NAME.getColumnNum(), volume.getLabel().length());
                }
                if (volume.getId() != null && columnWidthMap.get(ColumnEnum.ID.getColumnNum()) < volume.getId().toString().length()) {
                    columnWidthMap.put(ColumnEnum.ID.getColumnNum(), volume.getId().toString().length());
                }
                String personality = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.RP_PERSONALITY.toString(), volume.getVolumeInformation());
                if (personality != null && columnWidthMap.get(ColumnEnum.PERSONALITY.getColumnNum()) < personality.length()) {
                    columnWidthMap.put(ColumnEnum.PERSONALITY.getColumnNum(), personality.length());
                }
                String rpCopyName = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.RP_COPY_NAME.toString(), volume.getVolumeInformation());
                if (rpCopyName != null && columnWidthMap.get(ColumnEnum.COPY_NAME.getColumnNum()) < rpCopyName.length()) {
                    columnWidthMap.put(ColumnEnum.COPY_NAME.getColumnNum(), rpCopyName.length());
                }
                String rsetName = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.RP_RSET_NAME.toString(), volume.getVolumeInformation());
                if (rsetName != null && columnWidthMap.get(ColumnEnum.RSET_NAME.getColumnNum()) < rsetName.length()) {
                    columnWidthMap.put(ColumnEnum.RSET_NAME.getColumnNum(), rsetName.length());
                }
                columnWidthMap.put(ColumnEnum.VARRAY.getColumnNum(), "VARRAY".length());
                columnWidthMap.put(ColumnEnum.VPOOL.getColumnNum(), "VPOOL".length());
            }

            StringBuffer widthFormat = new StringBuffer();
            for (int column = 0; column < ColumnEnum.map.keySet().size() ; column++) {
                StringBuffer formatBuf = new StringBuffer();
                formatBuf.append("%");
                formatBuf.append(String.format("%d", columnWidthMap.get(column)));
                formatBuf.append("s ");
                sb.append(String.format(formatBuf.toString(), ColumnEnum.valueOf(column).name()));
                widthFormat.append(formatBuf.toString());
            }

            sb.append("\n");
            widthFormat.append("\n");
            
            // Now actually print the values
            for (UnManagedVolume volume : volumes) {
                String vArrayLabel = "N/A";
                String vPoolLabel = "N/A";
                String personality = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.RP_PERSONALITY.toString(), volume.getVolumeInformation());
                String rpCopyName = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.RP_COPY_NAME.toString(), volume.getVolumeInformation());
                String rsetName = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.RP_RSET_NAME.toString(), volume.getVolumeInformation());
                
                sb.append(String.format(widthFormat.toString(), 
                        volume.getLabel() != null ? volume.getLabel() : "N/A",
                        volume.getId() != null ? volume.getId() : "N/A",
                        personality != null ? personality : "N/A",
                        rpCopyName != null ? rpCopyName : "N/A",
                        rsetName != null ? rsetName : "N/A",
                        vArrayLabel,
                        vPoolLabel));
            }
        }
        
        return sb.toString();
    }
    
    @Override
    protected void validateAutoTierPolicy(String autoTierPolicyId, UnManagedVolume unManagedVolume, VirtualPool vPool) {
        super.validateAutoTierPolicy(autoTierPolicyId, unManagedVolume, vPool);
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
}

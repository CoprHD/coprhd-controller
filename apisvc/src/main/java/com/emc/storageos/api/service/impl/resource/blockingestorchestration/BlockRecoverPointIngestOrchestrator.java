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

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet.SupportedCGCharacteristics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.google.common.base.Joiner;

/**
 * RecoverPoint Ingestion
 * 
 * Ingestion of RecoverPoint is done one volume at a time, like any other ingestion.
 * The goal is to allow ingestion to occur in any order: journals, targets, and sources.
 * BlockConsistencyGroup and ProtectionSet objects are not created until all volumes associated
 * with the RP CG are ingested. All Volume objects should be flagged to not appear in the UI
 * during this intermediate phase. (NO_PUBLIC_ACCESS or similar)
 * 
 * Commmon RP Volume Ingestion Steps:
 * - A Volume object is created with respective personality field (SOURCE/TARGET/METADATA) filled-in
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

    // We want to allow customers to inventory-only delete volumes of volumes whose CG isn't fully ingested yet.
    public static final DataObject.Flag[] RP_INTERNAL_VOLUME_FLAGS = new DataObject.Flag[] { Flag.INTERNAL_OBJECT, Flag.SUPPORTS_FORCE,
            Flag.NO_METERING, Flag.NO_PUBLIC_ACCESS };

    private static final String LABEL_NA = "N/A";

    @Override
    protected void checkUnmanagedVolumeReplicas(UnManagedVolume unmanagedVolume) {
        return;
    }

    @Override
    public <T extends BlockObject> T ingestBlockObjects(IngestionRequestContext parentRequestContext, Class<T> clazz)
            throws IngestionException {

        if (parentRequestContext == null) {
            _logger.error("Parent request context is null.  Failing ingestion operation");
            throw IngestionException.exceptions.couldNotCreateVolume("Parent request context is null");
        }

        RecoverPointVolumeIngestionContext volumeContext = (RecoverPointVolumeIngestionContext) parentRequestContext.getVolumeContext();

        UnManagedVolume unManagedVolume = volumeContext.getUnmanagedVolume();
        boolean unManagedVolumeExported = volumeContext.isVolumeExported();

        // Validation checks on the unmanaged volume we're trying to ingest
        validateUnManagedVolumeProperties(unManagedVolume, volumeContext.getVarray(),
                volumeContext.getVpool(), volumeContext.getProject());

        BlockObject blockObject = volumeContext.getManagedBlockObject();

        // This ingestion orchestrator only deals with Volume objects. (snapshots, mirrors, clones aren't protected by RP)
        if (blockObject != null && !(blockObject instanceof Volume)) {
            _logger.error("Ingesting a non-volume object in RecoverPoint is not allowed: " + blockObject.getId().toString());
            throw IngestionException.exceptions.rpIngestingNonVolumeObject(unManagedVolume.getNativeGuid());
        }

        // Make sure there's an unmanaged protection set
        UnManagedProtectionSet umpset = volumeContext.getUnManagedProtectionSet();

        // Make sure there's an unmanaged protection set, and validate it
        if (umpset == null) {
            _logger.warn("No unmanaged protection set could be found for unmanaged volume: "
                    + volumeContext.getUnmanagedVolume().getNativeGuid()
                    + " Please run unmanaged CG discovery of registered protection system");
            throw IngestionException.exceptions.unManagedProtectionSetNotFound(
                    volumeContext.getUnmanagedVolume().getNativeGuid());
        }
        validateUnmanagedProtectionSet(volumeContext.getVpool(), unManagedVolume, umpset);

        // Test ingestion status message
        _logger.info("Printing Ingestion Report before Ingestion Attempt");
        _logger.info(getRPIngestionStatus(volumeContext));

        Volume volume = (Volume) blockObject;

        // Perform RP-specific volume ingestion
        volume = performRPVolumeIngestion(parentRequestContext, volumeContext, unManagedVolume, volume);

        // Decorate volume with RP Properties.
        decorateVolumeWithRPProperties(volumeContext, volume, unManagedVolume);

        // Update the unmanaged protection set
        decorateUnManagedProtectionSet(volumeContext, volume, unManagedVolume);

        // Perform RP-specific export ingestion
        performRPExportIngestion(volumeContext, unManagedVolume, volume);

        // Print post-ingestion report
        _logger.info("Printing Ingestion Report After Ingestion");
        _logger.info(getRPIngestionStatus(volumeContext));

        // Create the managed protection set/CG objects when we have all of the volumes ingested
        if (validateAllVolumesInCGIngested(parentRequestContext, volumeContext, unManagedVolume)) {
            _logger.info("Successfully ingested all volumes associated with RP consistency group");

            createProtectionSet(volumeContext);
            createBlockConsistencyGroup(volumeContext);

            // Once we have a proper managed consistency group and protection set, we need to
            // sprinkle those references over the managed volumes.
            decorateVolumeInformationFinalIngest(volumeContext);
        } else {
            volume.addInternalFlags(RP_INTERNAL_VOLUME_FLAGS); // Add internal flags
        }

        return clazz.cast(volume);
    }

    /**
     * Perform RP volume ingestion. Typically this involves finding the proper ingestion orchestrator
     * for the volume type (minus the fact it's RP, which got us to this code in the first place), then
     * calling block ingest on that orchestrator.
     * 
     * @param parentRequestContext the IngestionRequestContext for the overriding ingestion process
     * @param rpVolumeContext the RecoverPointVolumeIngestionContext for the volume currently being ingested
     * @param unManagedVolume unmanaged volume we're ingesting
     * @param volume resulting ingested volume
     * @return volume that is ingested
     */
    @SuppressWarnings("unchecked")
    private Volume performRPVolumeIngestion(IngestionRequestContext parentRequestContext,
            RecoverPointVolumeIngestionContext rpVolumeContext,
            UnManagedVolume unManagedVolume, Volume volume) {
        if (null == volume) {
            // We need to ingest the volume w/o the context of RP. (So, ingest a VMAX if it's VMAX, VPLEX if it's VPLEX, etc)

            IngestStrategy ingestStrategy = ingestStrategyFactory.buildIngestStrategy(unManagedVolume,
                    IngestStrategyFactory.DISREGARD_PROTECTION);

            volume = (Volume) ingestStrategy.ingestBlockObjects(rpVolumeContext,
                    VolumeIngestionUtil.getBlockObjectClass(unManagedVolume));

            _logger.info("Ingestion ended for unmanagedvolume {}", unManagedVolume.getNativeGuid());
            if (null == volume) {
                throw IngestionException.exceptions.generalVolumeException(
                        unManagedVolume.getLabel(), "check the logs for more details");
            }

        } else {
            // blockObject already ingested, now just update internalflags &
            // RP relationships. Run this logic always when volume NO_PUBLIC_ACCESS
            if (markUnManagedVolumeInactive(parentRequestContext, volume)) {
                _logger.info("All the related replicas and parent of unManagedVolume {} has been ingested ",
                        unManagedVolume.getNativeGuid());
                unManagedVolume.setInactive(true);
                // Add this unmanaged volume to the list of objects to be deleted if we succeed to run this whole ingestion.
                parentRequestContext.getUnManagedVolumesToBeDeleted().add(unManagedVolume);
            } else {
                _logger.info(
                        "Not all the parent/replicas of unManagedVolume {} have been ingested , hence marking as internal",
                        unManagedVolume.getNativeGuid());
                volume.addInternalFlags(RP_INTERNAL_VOLUME_FLAGS);
            }
        }

        rpVolumeContext.setManagedBlockObject(volume);
        if (null != _dbClient.queryObject(Volume.class, volume.getId())) {
            rpVolumeContext.addObjectToUpdate(volume);
        } else {
            rpVolumeContext.addObjectToCreate(volume);
        }

        return volume;
    }

    /**
     * Decorates the block objects with RP properties. Also updates the unmanaged volume object with
     * any references needed for future ingestions of RP volumes.
     * 
     * @param volumeContext the RecoverPointVolumeIngestionContext for the volume currently being ingested
     * @param volume volume that is the result of the ingest
     * @param unManagedVolume unmanaged volume with RP properties (VolumeInformation) on it
     */
    private void decorateVolumeWithRPProperties(RecoverPointVolumeIngestionContext volumeContext,
            Volume volume, UnManagedVolume unManagedVolume) {
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        String type = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.RP_PERSONALITY.toString(), unManagedVolumeInformation);
        if (Volume.PersonalityTypes.SOURCE.toString().equalsIgnoreCase(type)) {
            decorateUpdatesForRPSource(volumeContext, volume, unManagedVolume);
        } else if (Volume.PersonalityTypes.TARGET.toString().equalsIgnoreCase(type)) {
            decorateUpdatesForRPTarget(volumeContext, volume, unManagedVolume);
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
    }

    /**
     * Perform updates of the managed volume and associated unmanaged volumes and protection sets
     * given an RP source volume getting ingested.
     * 
     * @param volumeContext the RecoverPointVolumeIngestionContext for the volume currently being ingested
     * @param volume managed volume
     * @param unManagedVolume unmanaged volume
     */
    private void decorateUpdatesForRPSource(RecoverPointVolumeIngestionContext volumeContext,
            Volume volume, UnManagedVolume unManagedVolume) {
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        volume.setPersonality(PersonalityTypes.SOURCE.toString());
        volume.setAccessState(Volume.VolumeAccessState.READWRITE.toString());
        volume.setLinkStatus(Volume.LinkStatus.IN_SYNC.toString());

        // When we ingest a source volume, we need to properly create the RP Target list for that source,
        // however it is possible that not all (or any) of the RP targets have been ingested yet. Therefore
        // we need to do as much as we can:
        //
        // 1. Process each managed target volume ID in the unmanaged source volume, add to the managed source volume's RP target list.
        // 2. Go through each unmanaged RP target volume in the unmanaged source volume (before it goes away), add the managed source volume
        // ID.
        // 3. Go through each unmanaged RP target volume in the unmanaged source volume, remove the unmanaged source volume ID.

        // 1. Process each managed target volume ID in the unmanaged source volume, add to the managed source volume's RP target list.
        StringSet rpManagedTargetVolumeIdStrs = PropertySetterUtil.extractValuesFromStringSet(
                SupportedVolumeInformation.RP_MANAGED_TARGET_VOLUMES.toString(),
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

        // 2. Go through each unmanaged RP target volume in the unmanaged source volume (before it goes away), add the managed source volume
        // ID.
        // 3. Go through each unmanaged RP target volume in the unmanaged source volume, remove the unmanaged source volume ID.
        StringSet rpUnManagedTargetVolumeIdStrs = PropertySetterUtil.extractValuesFromStringSet(
                SupportedVolumeInformation.RP_UNMANAGED_TARGET_VOLUMES.toString(),
                unManagedVolumeInformation);
        for (String rpUnManagedTargetVolumeIdStr : rpUnManagedTargetVolumeIdStrs) {
            UnManagedVolume unManagedTargetVolume = _dbClient.queryObject(UnManagedVolume.class, URI.create(rpUnManagedTargetVolumeIdStr));
            if (unManagedTargetVolume == null) {
                _logger.error("Could not find unmanaged target volume: " + rpUnManagedTargetVolumeIdStr + " in DB.  Ingestion failed.");
                throw IngestionException.exceptions.noUnManagedTargetVolumeFound(unManagedVolume.getNativeGuid(),
                        rpUnManagedTargetVolumeIdStr);
            }

            // (2) Add the managed source volume ID to this target that hasn't been ingested yet, so when it IS ingested, we know
            // what RP source it belongs to.
            StringSet rpManagedSourceVolumeId = new StringSet();
            rpManagedSourceVolumeId.add(volume.getId().toString());
            unManagedTargetVolume.putVolumeInfo(SupportedVolumeInformation.RP_MANAGED_SOURCE_VOLUME.toString(),
                    rpManagedSourceVolumeId);

            // (3) Remove the unmanaged source volume ID to this target that is going away as a result of ingestion.
            // This is for completeness. The ID is going away in the DB, so we don't want any references to it anywhere.
            StringSet rpUnManagedSourceVolumeId = new StringSet();
            unManagedTargetVolume.putVolumeInfo(SupportedVolumeInformation.RP_UNMANAGED_SOURCE_VOLUME.toString(),
                    rpUnManagedSourceVolumeId);

            volumeContext.addUnmanagedTargetVolumeToUpdate(unManagedTargetVolume);
        }
    }

    /**
     * Perform updates of the managed volume and associated unmanaged volumes and protection sets
     * given an RP target volume getting ingested.
     * 
     * @param volumeContext the RecoverPointVolumeIngestionContext for the volume currently being ingested
     * @param volume managed volume
     * @param unManagedVolume unmanaged volume
     */
    private void decorateUpdatesForRPTarget(RecoverPointVolumeIngestionContext volumeContext,
            Volume volume, UnManagedVolume unManagedVolume) {
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        // If the target volume is unexported, check if it is in image access state
        if (!VolumeIngestionUtil.checkUnManagedResourceIsNonRPExported(unManagedVolume)
                && VolumeIngestionUtil.isRPUnManagedVolumeInImageAccessState(unManagedVolume)) {
            String rpAccessState = PropertySetterUtil.extractValueFromStringSet(SupportedVolumeInformation.RP_ACCESS_STATE.toString(),
                    unManagedVolume.getVolumeInformation());
            _logger.error("RP target unmanaged volume is not exported and is in image access state: " + rpAccessState);
            throw IngestionException.exceptions.rpUnManagedTargetVolumeInImageAccessState(unManagedVolume.getNativeGuid(), rpAccessState);
        }
        volume.setPersonality(PersonalityTypes.TARGET.toString());
        volume.setAccessState(Volume.VolumeAccessState.NOT_READY.toString());
        volume.setLinkStatus(Volume.LinkStatus.IN_SYNC.toString());

        // Any time a target goes from UnManaged -> Managed, we need to ensure that:
        // 1. If there is a source managed volume, it gets the managed target volume added to its RP Target List
        // 2. If there is a source Unmanaged volume, the managed target volume added to its RP_MANAGED_TARGET_VOLUMES list
        // 3. If there is a source Unmanaged volume, the unmanaged target volume is removed from the RP_UNMANAGED_TARGET_VOLUMES list
        //
        // This ensures that we don't lose track of sources and targets, regardless of the order volumes are ingested and unmanaged volumes
        // are deleted during the ingestion process.

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
            volumeContext.addManagedSourceVolumeToUpdate(sourceVolume);
        } else {
            _logger.info("There is no ingested RP source volume associated with this target yet: " + volume.getLabel());

            String rpUnManagedSourceVolume = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.RP_UNMANAGED_SOURCE_VOLUME.toString(), unManagedVolumeInformation);
            if (rpUnManagedSourceVolume == null) {
                _logger.error("There is no uningested RP source volume associated with this target either.  This is an error condition: "
                        + volume.getLabel());
                throw IngestionException.exceptions.noUnManagedSourceVolumeFound(unManagedVolume.getNativeGuid());
            }

            // (2) Add the managed target to the RP_MANAGED_TARGET_VOLUMES list associated with the unmanaged source volume
            UnManagedVolume unManagedSourceVolume = _dbClient.queryObject(UnManagedVolume.class, URI.create(rpUnManagedSourceVolume));
            if (unManagedSourceVolume == null) {
                _logger.error("Could not find unmanaged RP source volume in DB: " + rpUnManagedSourceVolume);
                throw IngestionException.exceptions.noUnManagedSourceVolumeFound2(unManagedVolume.getNativeGuid(), rpUnManagedSourceVolume);
            }

            StringSet rpManagedTargetVolumeIdStrs = PropertySetterUtil.extractValuesFromStringSet(
                    SupportedVolumeInformation.RP_MANAGED_TARGET_VOLUMES.toString(),
                    unManagedSourceVolume.getVolumeInformation());
            rpManagedTargetVolumeIdStrs.add(volume.getId().toString());
            unManagedSourceVolume.putVolumeInfo(SupportedVolumeInformation.RP_MANAGED_TARGET_VOLUMES.toString(),
                    rpManagedTargetVolumeIdStrs);

            // (3) Remove the unmanaged target from the RP_UNMANAGED_TARGET_VOLUMES list associated with the unmanaged source volume
            StringSet rpUnManagedTargetVolumeIdStrs = PropertySetterUtil.extractValuesFromStringSet(
                    SupportedVolumeInformation.RP_UNMANAGED_TARGET_VOLUMES.toString(),
                    unManagedSourceVolume.getVolumeInformation());
            rpUnManagedTargetVolumeIdStrs.remove(unManagedVolume.getId().toString());
            unManagedSourceVolume.putVolumeInfo(SupportedVolumeInformation.RP_UNMANAGED_TARGET_VOLUMES.toString(),
                    rpUnManagedTargetVolumeIdStrs);

            volumeContext.addUnmanagedSourceVolumeToUpdate(unManagedSourceVolume);
        }
    }

    /**
     * The unmanaged protection is responsible for keeping track of the managed and unmanaged volumes that
     * are associated with the RP CG. This method keeps those managed and unmanaged IDs up to date.
     * 
     * @param volumeContext the RecoverPointVolumeIngestionContext for the volume currently being ingested
     * @param umpset unmanaged protection set to update
     * @param volume the managed volume
     * @param unManagedVolume the unmanaged volume
     */
    private void decorateUnManagedProtectionSet(RecoverPointVolumeIngestionContext volumeContext,
            Volume volume, UnManagedVolume unManagedVolume) {
        UnManagedProtectionSet umpset = volumeContext.getUnManagedProtectionSet();

        // Add the volume to the list of managed volumes we have so far.
        if (!umpset.getManagedVolumeIds().contains(volume.getId().toString())) {
            umpset.getManagedVolumeIds().add(volume.getId().toString());
        }

        // Remove the unmanaged volume from the list we have so far since that is going inactive.
        if (umpset.getUnManagedVolumeIds().contains(unManagedVolume.getId().toString())) {
            umpset.getUnManagedVolumeIds().remove(unManagedVolume.getId().toString());
        }

        // Set up the unmanaged protection set object to be updated
        volumeContext.addObjectToUpdate(umpset);
    }

    /**
     * This method will perform all of the final decorations (attribute setting) on the Volume
     * object after creating the required BlockConsistencyGroup and ProtectionSet objects.
     * 
     * Fields such as rpCopyName and rSetName were already filled in when we did the ingest of
     * the volume itself. In this method, we worry about stitching together all of the object
     * references within the Volume object so it will act like a native CoprHD-created RP volume.
     * 
     * @param volumeContext the RecoverPointVolumeIngestionContext for the volume currently being ingested
     */
    private void decorateVolumeInformationFinalIngest(RecoverPointVolumeIngestionContext volumeContext) {

        ProtectionSet pset = volumeContext.getManagedProtectionSet();
        BlockConsistencyGroup cg = volumeContext.getManagedBlockConsistencyGroup();

        if (pset.getVolumes() == null) {
            _logger.error("No volumes found in protection set: " + pset.getLabel() + ", cannot process ingestion");
            throw IngestionException.exceptions.noVolumesFoundInProtectionSet(pset.getLabel());
        }

        Iterator<Volume> volumesItr = _dbClient.queryIterativeObjects(Volume.class, URIUtil.toURIList(pset.getVolumes()));
        List<Volume> volumes = new ArrayList<Volume>();
        while (volumesItr.hasNext()) {
            volumes.add(volumesItr.next());
        }

        // Make sure all of the changed managed block objects get updated
        volumes.add((Volume) volumeContext.getManagedBlockObject());
        List<DataObject> updatedObjects = new ArrayList<DataObject>();

        VolumeIngestionUtil.decorateRPVolumesCGInfo(volumes, pset, cg, updatedObjects, _dbClient);
        clearPersistedReplicaFlags(volumes, updatedObjects);
        clearReplicaFlagsInIngestionContext(volumeContext);

        for (DataObject volume : updatedObjects) {
            if (!volumeContext.getManagedBlockObject().getId().equals(volume.getId())) {
                // add all volumes except the newly ingested one to the update list
                volumeContext.addObjectToUpdate(volume);
            }
        }
    }

    /**
     * Clear the flags of replicas which have been updated during the ingestion process
     * 
     * @param volumeContext
     */
    private void clearReplicaFlagsInIngestionContext(RecoverPointVolumeIngestionContext volumeContext) {
        for (List<DataObject> updatedObjects : volumeContext.getObjectsToBeUpdatedMap().values()) {
            for (DataObject updatedObject : updatedObjects) {
                if (updatedObject instanceof BlockMirror || updatedObject instanceof BlockSnapshot
                        || (updatedObject instanceof Volume && ((Volume) updatedObject).getAssociatedSourceVolume() != null)) {
                    updatedObject.clearInternalFlags(INTERNAL_VOLUME_FLAGS);
                }
            }
        }
    }

    /**
     * RecoverPoint volumes are expected to have export masks where the volume is exported to
     * a RecoverPoint site. Therefore every RP volume (sources, targets, journals) will need to
     * go through this code and have their export mask ingested. Even if the mask has already been
     * ingested by a previous volume ingestion, this method still needs to update the ExportGroup and
     * ExportMask objects to reflect the newly ingested volume as part of its management.
     * 
     * @param volumeContext the RecoverPointVolumeIngestionContext for the volume currently being ingested
     * @param unManagedVolume unmanaged volume
     * @param volume managed volume
     * @return managed volume with export ingested
     */
    private void performRPExportIngestion(IngestionRequestContext volumeContext,
            UnManagedVolume unManagedVolume, Volume volume) {

        VirtualArray virtualArray = volumeContext.getVarray();
        Project project = volumeContext.getProject();

        // TODO: In the case where the source or target is exported to a host as well, VMAX2 best practices dictate that you
        // create separate MVs for each host's RP volumes. That would mean a different export group per host/cluster.
        ProtectionSystem protectionSystem = _dbClient.queryObject(ProtectionSystem.class, volume.getProtectionController());
        UnManagedExportMask em = findUnManagedRPExportMask(protectionSystem, unManagedVolume);
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, volume.getStorageController());

        if (em == null) {
            _logger.error("Could not find an unmanaged export mask associated with volume: " + unManagedVolume.getLabel());
            throw IngestionException.exceptions.noUnManagedExportMaskFound(unManagedVolume.getNativeGuid());
        }

        ExportGroup exportGroup = VolumeIngestionUtil.verifyExportGroupExists(project.getId(), em.getKnownInitiatorUris(),
                virtualArray.getId(), _dbClient);
        if (null == exportGroup) {
            volumeContext.setExportGroupCreated(true);
            Integer numPaths = em.getZoningMap().size();
            _logger.info("Creating Export Group with label {}", em.getMaskName());
            // No existing group has the mask, let's create one.
            //TODO: Bharath - setting the isJournal to false - double check to see if this can be inferred
            exportGroup = RPHelper.createRPExportGroup(volume.getInternalSiteName(), virtualArray, project, protectionSystem,
                    storageSystem, numPaths, false);
        }

        volumeContext.setExportGroup(exportGroup);

        // set RP device initiators to be used as the "host" for export mask ingestion
        List<Initiator> initiators = new ArrayList<Initiator>();
        Iterator<Initiator> initiatorItr = _dbClient.queryIterativeObjects(Initiator.class, URIUtil.toURIList(em.getKnownInitiatorUris()));
        while (initiatorItr.hasNext()) {
            initiators.add(initiatorItr.next());
        }
        volumeContext.setDeviceInitiators(initiators);

        // find the ingest export strategy and call into for this unmanaged export mask
        IngestExportStrategy ingestStrategy = ingestStrategyFactory.buildIngestExportStrategy(unManagedVolume);
        volume = ingestStrategy.ingestExportMasks(unManagedVolume, volume, volumeContext);

        if (null == volume) {
            // an exception should have been thrown by a lower layer in
            // ingestion did not succeed, but in case it wasn't, throw one
            throw IngestionException.exceptions.generalVolumeException(
                    unManagedVolume.getLabel(), "check the logs for more details");
        }
    }

    /**
     * This unmanaged volume may be associated with several export masks. We need to find the export mask
     * that belongs specifically to the RP protection system supplied.
     * 
     * Note: There should only be one (1) mask that contains both the protection system's initiators AND the volume.
     * If this is not true, this method (and its caller) need to be reconsidered.
     * 
     * @param protectionSystem protection system
     * @param unManagedVolume unmanaged volume
     * @return unmanaged export mask that belongs to the protection system that contains the unmanaged volume
     */
    private UnManagedExportMask findUnManagedRPExportMask(ProtectionSystem protectionSystem, UnManagedVolume unManagedVolume) {
        UnManagedExportMask em;
        for (String maskIdStr : unManagedVolume.getUnmanagedExportMasks()) {

            // Find the mask associated with the protection system. (Assume there's only one for this volume)
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
                        _logger.info(String
                                .format("Found UnManagedVolume %s was found in UnManagedExportMask %s and will be ingested (if not ingested already)",
                                        unManagedVolume.getLabel(), em.getMaskName()));
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
     * @param parentRequestContext parent request context object
     * @param volumeContext ingestion context object
     * @param unManagedVolume unmanaged volume object
     * 
     * @return true if all volumes in CG are ingested
     */
    private boolean validateAllVolumesInCGIngested(IngestionRequestContext parentRequestContext,
            RecoverPointVolumeIngestionContext volumeContext, UnManagedVolume unManagedVolume) {
        UnManagedProtectionSet umpset = volumeContext.getUnManagedProtectionSet();
        if (umpset == null) {
            _logger.error("Unable to find unmanaged protection set associated with volume: " + unManagedVolume.getId()
                    + " Please run unmanaged CG discovery of registered protection systems");
            throw IngestionException.exceptions.unManagedProtectionSetNotFound(unManagedVolume.getNativeGuid());
        }

        return VolumeIngestionUtil.validateAllVolumesInCGIngested(parentRequestContext.getUnManagedVolumesToBeDeleted(), umpset, _dbClient);
    }

    /**
     * Create the managed protection set associated with the ingested RP volumes.
     * Also, as a side-effect, insert the protection set ID into each of the impacted volumes.
     * 
     * @param volumeContext the RecoverPointVolumeIngestionContext for the volume currently being ingested
     * @return a new protection set object
     */
    private ProtectionSet createProtectionSet(RecoverPointVolumeIngestionContext volumeContext) {
        UnManagedProtectionSet umpset = volumeContext.getUnManagedProtectionSet();
        ProtectionSet pset = VolumeIngestionUtil.createProtectionSet(umpset, _dbClient);
        volumeContext.setManagedProtectionSet(pset);
        return pset;
    }

    /**
     * Create the block consistency group object associated with the CG as part of ingestion.
     * 
     * @param volumeContext the RecoverPointVolumeIngestionContext for the volume currently being ingested
     * @return a new block consistency group object
     */
    private BlockConsistencyGroup createBlockConsistencyGroup(RecoverPointVolumeIngestionContext volumeContext) {
        ProtectionSet pset = volumeContext.getManagedProtectionSet();
        BlockConsistencyGroup cg = VolumeIngestionUtil.createRPBlockConsistencyGroup(pset, _dbClient);
        volumeContext.setManagedBlockConsistencyGroup(cg);
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
        // For example, you could put a check in here that ensures that the TARGET/METADATA are associated
        // with some RP vpool. It would be good to fail the ingestion early with an error that says "You're
        // trying to ingest this target/journal volume in a vpool that is not associated with a RP vpool."

        // First check: Make sure a SOURCE vpool is being ingested with an RP vpool (and not a target/base vpool)
        String type = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.RP_PERSONALITY.toString(), unManagedVolume.getVolumeInformation());
        _logger.info("Type found: " + type);
        if ((Volume.PersonalityTypes.SOURCE.toString().equalsIgnoreCase(type)) &&
                (virtualPool.getProtectionVarraySettings() == null)) {
            throw IngestionException.exceptions.invalidSourceRPVirtualPool(unManagedVolume.getLabel(), virtualPool.getLabel());
        } 

        if (VolumeIngestionUtil.checkUnManagedVolumeHasReplicas(unManagedVolume)) {
            // check if the RP protected volume has any mirrors. If yes, throw an error as we don't support this configuration in ViPR as of
            // now
            StringSet mirrors = PropertySetterUtil.extractValuesFromStringSet(SupportedVolumeInformation.MIRRORS.toString(),
                    unManagedVolume.getVolumeInformation());
            if (mirrors != null && !mirrors.isEmpty()) {
                String mirrorsString = Joiner.on(", ").join(mirrors);
                _logger.info("Unmanaged RP volume {} has mirrors: {} associated which is not supported", unManagedVolume.getLabel(),
                        mirrorsString);
                throw IngestionException.exceptions.rpUnManagedVolumeCannotHaveMirrors(unManagedVolume.getLabel(), mirrorsString);
            }
            // If the RP volume has snaps, check if the vpool allows snaps.
            StringSet snapshots = PropertySetterUtil.extractValuesFromStringSet(SupportedVolumeInformation.SNAPSHOTS.toString(),
                    unManagedVolume.getVolumeInformation());
            if (snapshots != null && !snapshots.isEmpty()) {
                int numOfSnaps = snapshots.size();
                if (VirtualPool.vPoolSpecifiesSnapshots(virtualPool)) {
                    if (numOfSnaps > virtualPool.getMaxNativeSnapshots()) {
                        String reason = "volume has more snapshots (" + numOfSnaps + ") than vpool allows";
                        _logger.error(reason);
                        throw IngestionException.exceptions.validationException(reason);
                    }
                } else {
                    String reason = "vpool does not allow snapshots, but volume has " + numOfSnaps + " snapshot(s)";
                    _logger.error(reason);
                    throw IngestionException.exceptions.validationException(reason);
                }
            }
        }
    }

    /**
     * Validate the unmanaged protection set before ingesting the volume. Is the CG healthy? Does the protection set's
     * policies match the vpool (in the case of RP source volumes)
     * 
     * @param vpool virtual pool
     * @param unManagedVolume unmanaged volume attempted to be ingested
     * @param umpset unmanaged protection set with settings/state information
     */
    private void validateUnmanagedProtectionSet(VirtualPool vpool, UnManagedVolume unManagedVolume, UnManagedProtectionSet umpset) {
        if (umpset == null) {
            _logger.warn("No unmanaged protection set could be found for unmanaged volume: " + unManagedVolume.getNativeGuid()
                    + " Please run unmanaged CG discovery of registered protection system");
            throw IngestionException.exceptions.unManagedProtectionSetNotFound(unManagedVolume.getNativeGuid());
        }

        // Check the health of the consistency group first. This applies to any volume associated with an RP CG.
        String rpHealthy = umpset.getCGCharacteristics()
                .get(SupportedCGCharacteristics.IS_HEALTHY.toString());
        if (!Boolean.valueOf(rpHealthy.toUpperCase())) {
            _logger.error(String.format("At the time of discovery, the RecoverPoint consistency group %s associated "
                    + "with unmanaged volume %s was in an unhealthy state (disabled, paused, or in error). If the issue "
                    + "has been resolved, rerun discovery of unmanaged consistency groups for this protection system.",
                    umpset.getCgName(),
                    unManagedVolume.getNativeGuid()));
            throw IngestionException.exceptions.unManagedProtectionSetNotHealthy(umpset.getCgName(), unManagedVolume.getNativeGuid());
        }

        // Specifically for RP source volumes: Make sure the sync/async of the vpool aligns with the protection set.
        String personality = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.RP_PERSONALITY.toString(), unManagedVolume.getVolumeInformation());
        if (personality == null) {
            _logger.error("Could not find the personality of unmanaged volume " + unManagedVolume.getLabel()
                    + ". Run unmanaged consistency group discovery for this protection system.");
            throw IngestionException.exceptions.rpObjectNotSet("Personality", unManagedVolume.getId());
        }

        if (Volume.PersonalityTypes.SOURCE.toString().equalsIgnoreCase(personality)) {
            String rpSync = umpset.getCGCharacteristics()
                    .get(SupportedCGCharacteristics.IS_SYNC.toString());
            // rpCopyMode is allowed to be blank, and blank defaults to ASYNC on the RP appliance.
            String rpCopyMode = (vpool.getRpCopyMode() != null) ? vpool.getRpCopyMode() : VirtualPool.RPCopyMode.ASYNCHRONOUS.toString();
            if (Boolean.valueOf(rpSync.toUpperCase()) && rpCopyMode.equalsIgnoreCase(VirtualPool.RPCopyMode.ASYNCHRONOUS.toString())) {
                _logger.error(String.format("The RecoverPoint consistency group %s associated with unmanaged volume %s is "
                        + "running in synchronous mode, but the virtual pool requires asynchronous mode. Modify virtual pool settings "
                        + "or create a new virtual pool, rerun unmanaged consistency group discovery, and then rerun ingestion.",
                        umpset.getCgName(),
                        unManagedVolume.getNativeGuid()));
                throw IngestionException.exceptions.unManagedProtectionSetNotAsync(umpset.getCgName(), unManagedVolume.getNativeGuid());
            }

            if (!Boolean.valueOf(rpSync.toUpperCase()) && rpCopyMode.equalsIgnoreCase(VirtualPool.RPCopyMode.SYNCHRONOUS.toString())) {
                _logger.error(String.format("The RecoverPoint consistency group %s associated with unmanaged volume %s is "
                        + "running in asynchronous mode, but the virtual pool requires synchronous mode. Modify virtual pool "
                        + "settings or create a new virtual pool, rerun unmanaged consistency group discovery, and then rerun ingestion.",
                        umpset.getCgName(),
                        unManagedVolume.getNativeGuid()));
                throw IngestionException.exceptions.unManagedProtectionSetNotSync(umpset.getCgName(), unManagedVolume.getNativeGuid());
            }
        }
    }

    private enum ColumnEnum {
        NAME(0), ID(1), PERSONALITY(2), COPY_NAME(3), RSET_NAME(4), VARRAY(5), VPOOL(6);

        private final int column;

        private static Map<Integer, ColumnEnum> map = new HashMap<Integer, ColumnEnum>();

        static {
            for (ColumnEnum columnEnum : ColumnEnum.values()) {
                map.put(columnEnum.column, columnEnum);
            }
        }

        private ColumnEnum(final int column) {
            this.column = column;
        }

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
     * @param volumeContext context information
     * 
     * @return String status (multi-line, formatted)
     */
    private String getRPIngestionStatus(RecoverPointVolumeIngestionContext volumeContext) {
        StringBuffer sb = new StringBuffer();

        UnManagedProtectionSet umpset = volumeContext.getUnManagedProtectionSet();

        sb.append("\nRecoverPoint Ingestion Progress Report\n");
        sb.append("--------------------------------------\n");

        sb.append("RP CG Name:        " + umpset.getCgName() + "\n");
        if (umpset.getProtectionSystemUri() != null) {
            ProtectionSystem ps = _dbClient.queryObject(ProtectionSystem.class, umpset.getProtectionSystemUri());
            sb.append("Protection System: " + ps.getLabel() + " [" + ps.getIpAddress() + "]");
        }

        // Keep track of the column widths
        Map<Integer, Integer> columnWidthMap = new HashMap<Integer, Integer>();
        for (int column = 0; column < ColumnEnum.map.keySet().size(); column++) {
            columnWidthMap.put(column, ColumnEnum.valueOf(column).toString().length());
        }

        if (!umpset.getManagedVolumeIds().isEmpty()) {
            List<Volume> volumes = new ArrayList<Volume>();
            sb.append("\n\nIngested Volumes:\n");
            for (URI volumeId : URIUtil.toURIList(umpset.getManagedVolumeIds())) {
                Volume volume = _dbClient.queryObject(Volume.class, volumeId);
                if (volume == null) {
                    // Check the "just created" list in the volume context
                    for (BlockObject blockObject : volumeContext.getObjectsToBeCreatedMap().values()) {
                        if (blockObject.getId().toString().equals(volumeId.toString())) {
                            if (blockObject instanceof Volume) {
                                volume = (Volume) blockObject;
                                break;
                            }
                        }
                    }
                    if (volume != null) {
                        volumes.add(volume);
                    } else {
                        continue;
                    }
                } else {
                    volumes.add(volume);
                }

                // Get the width of the columns so we can have a compact, formatted printout.
                // Name, ID, Personality, copy name, rset name, varray name, vpool name
                if (volume.getLabel() != null && columnWidthMap.get(ColumnEnum.NAME.getColumnNum()) < volume.getLabel().length()) {
                    columnWidthMap.put(ColumnEnum.NAME.getColumnNum(), volume.getLabel().length());
                }
                if (volume.getId() != null && columnWidthMap.get(ColumnEnum.ID.getColumnNum()) < volume.getId().toString().length()) {
                    columnWidthMap.put(ColumnEnum.ID.getColumnNum(), volume.getId().toString().length());
                }
                if (volume.getPersonality() != null
                        && columnWidthMap.get(ColumnEnum.PERSONALITY.getColumnNum()) < volume.getPersonality().length()) {
                    columnWidthMap.put(ColumnEnum.PERSONALITY.getColumnNum(), volume.getPersonality().length());
                }
                if (volume.getRpCopyName() != null
                        && columnWidthMap.get(ColumnEnum.COPY_NAME.getColumnNum()) < volume.getRpCopyName().length()) {
                    columnWidthMap.put(ColumnEnum.COPY_NAME.getColumnNum(), volume.getRpCopyName().length());
                }
                if (volume.getRSetName() != null && columnWidthMap.get(ColumnEnum.RSET_NAME.getColumnNum()) < volume.getRSetName().length()) {
                    columnWidthMap.put(ColumnEnum.RSET_NAME.getColumnNum(), volume.getRSetName().length());
                }
                if (volume.getVirtualArray() != null) {
                    VirtualArray vArray = _dbClient.queryObject(VirtualArray.class, volume.getVirtualArray());
                    if (vArray != null && vArray.getLabel() != null
                            && columnWidthMap.get(ColumnEnum.RSET_NAME.getColumnNum()) < vArray.getLabel().length()) {
                        columnWidthMap.put(ColumnEnum.VARRAY.getColumnNum(), vArray.getLabel().length());
                    }
                }
                if (volume.getVirtualPool() != null) {
                    VirtualPool vPool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
                    if (vPool != null && vPool.getLabel() != null
                            && columnWidthMap.get(ColumnEnum.RSET_NAME.getColumnNum()) < vPool.getLabel().length()) {
                        columnWidthMap.put(ColumnEnum.VPOOL.getColumnNum(), vPool.getLabel().length());
                    }
                }
            }

            StringBuffer widthFormat = new StringBuffer();
            for (int column = 0; column < ColumnEnum.map.keySet().size(); column++) {
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
                String vArrayLabel = LABEL_NA;
                String vPoolLabel = LABEL_NA;

                if (volume.getVirtualArray() != null) {
                    VirtualArray vArray = _dbClient.queryObject(VirtualArray.class, volume.getVirtualArray());
                    vArrayLabel = vArray.getLabel();
                }

                if (volume.getVirtualPool() != null) {
                    VirtualPool vPool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
                    vPoolLabel = vPool.getLabel();
                }

                sb.append(String.format(widthFormat.toString(),
                        volume.getLabel() != null ? volume.getLabel() : LABEL_NA,
                        volume.getId() != null ? volume.getId() : LABEL_NA,
                        volume.getPersonality() != null ? volume.getPersonality() : LABEL_NA,
                        volume.getRpCopyName() != null ? volume.getRpCopyName() : LABEL_NA,
                        volume.getRSetName() != null ? volume.getRSetName() : LABEL_NA,
                        vArrayLabel,
                        vPoolLabel));
            }
        }

        // Keep track of the column widths
        columnWidthMap.clear();
        for (int column = 0; column < ColumnEnum.map.keySet().size(); column++) {
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
                columnWidthMap.put(ColumnEnum.VARRAY.getColumnNum(), ColumnEnum.VARRAY.name().length());
                columnWidthMap.put(ColumnEnum.VPOOL.getColumnNum(), ColumnEnum.VPOOL.name().length());
            }

            StringBuffer widthFormat = new StringBuffer();
            for (int column = 0; column < ColumnEnum.map.keySet().size(); column++) {
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
                String vArrayLabel = LABEL_NA;
                String vPoolLabel = LABEL_NA;
                String personality = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.RP_PERSONALITY.toString(), volume.getVolumeInformation());
                String rpCopyName = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.RP_COPY_NAME.toString(), volume.getVolumeInformation());
                String rsetName = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.RP_RSET_NAME.toString(), volume.getVolumeInformation());

                sb.append(String.format(widthFormat.toString(),
                        volume.getLabel() != null ? volume.getLabel() : LABEL_NA,
                        volume.getId() != null ? volume.getId() : LABEL_NA,
                        personality != null ? personality : LABEL_NA,
                        rpCopyName != null ? rpCopyName : LABEL_NA,
                        rsetName != null ? rsetName : LABEL_NA,
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

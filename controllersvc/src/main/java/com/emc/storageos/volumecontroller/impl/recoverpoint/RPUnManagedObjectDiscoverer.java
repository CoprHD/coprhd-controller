/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.recoverpoint;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet.SupportedCGInformation;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.recoverpoint.impl.RecoverPointClient;
import com.emc.storageos.recoverpoint.responses.GetCGsResponse;
import com.emc.storageos.recoverpoint.responses.GetCGsResponse.GetCGStateResponse;
import com.emc.storageos.recoverpoint.responses.GetCopyResponse;
import com.emc.storageos.recoverpoint.responses.GetRSetResponse;
import com.emc.storageos.recoverpoint.responses.GetVolumeResponse;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

public class RPUnManagedObjectDiscoverer {

    private static final Logger log = LoggerFactory.getLogger(RPUnManagedObjectDiscoverer.class);
    private static final String UNMANAGED_PROTECTION_SET = "UnManagedProtectionSet";
    private static final String UNMANAGED_VOLUME = "UnManagedVolume";
    private static final int BATCH_SIZE = 100;

    private List<UnManagedProtectionSet> unManagedCGsInsert = null;
    private List<UnManagedProtectionSet> unManagedCGsUpdate = null;
    private List<UnManagedVolume> unManagedVolumesToDelete = null;
    private Map<String, UnManagedVolume> unManagedVolumesToUpdateByWwn = null;
    private Set<URI> unManagedCGsReturnedFromProvider = null;

    private PartitionManager partitionManager;

    /**
     * Discovers the RP CGs and all the volumes therein. It updates/creates the UnManagedProtectionSet
     * objects and updates (if it exists) the UnManagedVolume objects with RP information needed for
     * ingestion
     * 
     * @param accessProfile access profile
     * @param dbClient db client
     * @param partitionManager partition manager
     * @throws Exception
     */
    public void discoverUnManagedObjects(AccessProfile accessProfile, DbClient dbClient,
            PartitionManager partitionManager) throws Exception {

        this.partitionManager = partitionManager;

        log.info("Started discovery of UnManagedVolumes for system {}", accessProfile.getSystemId());
        ProtectionSystem protectionSystem = dbClient.queryObject(ProtectionSystem.class, accessProfile.getSystemId());
        if (protectionSystem == null) {
            log.error("Discovery is not run!  Protection System not found: " + accessProfile.getSystemId());
            return;
        }

        RecoverPointClient rp = RPHelper.getRecoverPointClient(protectionSystem);

        unManagedCGsInsert = new ArrayList<UnManagedProtectionSet>();
        unManagedCGsUpdate = new ArrayList<UnManagedProtectionSet>();
        unManagedVolumesToDelete = new ArrayList<UnManagedVolume>();
        unManagedVolumesToUpdateByWwn = new HashMap<String, UnManagedVolume>();
        unManagedCGsReturnedFromProvider = new HashSet<URI>();

        // Get all of the consistency groups (and their volumes) from RP
        Set<GetCGsResponse> cgs = rp.getAllCGs();

        if (cgs == null) {
            log.warn("No CGs were found on protection system: " + protectionSystem.getLabel());
            return;
        }

        // This section of code allows us to cache XIO native GUID to workaround an issue
        // with RP's understanding of XIO volume WWNs (128-bit) and the rest of the world's
        // understanding of the XIO volume WWN once it's exported (64-bit)
        Map<String, String> rpWwnToNativeWwn = new HashMap<String, String>();
        List<URI> storageSystemIds = dbClient.queryByType(StorageSystem.class, true);
        List<String> storageNativeIdPrefixes = new ArrayList<String>();
        if (storageSystemIds != null) {
            Iterator<StorageSystem> storageSystemsItr = dbClient.queryIterativeObjects(StorageSystem.class, storageSystemIds);
            while (storageSystemsItr.hasNext()) {
                StorageSystem storageSystem = storageSystemsItr.next();
                if (storageSystem.getSystemType().equalsIgnoreCase(Type.xtremio.name())) {
                    storageNativeIdPrefixes.add(storageSystem.getNativeGuid());
                }
            }
        }

        for (GetCGsResponse cg : cgs) {
            try {
                log.info("Processing returned CG: " + cg.getCgName());
                boolean newCG = false;

                // UnManagedProtectionSet native GUID is protection system GUID + consistency group ID
                String nativeGuid = protectionSystem.getNativeGuid() + Constants.PLUS + cg.getCgId();

                // First check to see if this protection set is already part of our managed DB
                if (null != DiscoveryUtils.checkProtectionSetExistsInDB(dbClient, nativeGuid)) {
                    log.info("Protection Set " + nativeGuid + " already is managed by ViPR, skipping unmanaged discovery");
                    continue;
                }

                // Now check to see if the unmanaged CG exists in the database
                UnManagedProtectionSet unManagedProtectionSet = DiscoveryUtils.checkUnManagedProtectionSetExistsInDB(dbClient, nativeGuid);

                if (null == unManagedProtectionSet) {
                    log.info("Creating new unmanaged protection set for CG: " + cg.getCgName());
                    unManagedProtectionSet = new UnManagedProtectionSet();
                    unManagedProtectionSet.setId(URIUtil.createId(UnManagedProtectionSet.class));
                    unManagedProtectionSet.setNativeGuid(nativeGuid);
                    unManagedProtectionSet.setProtectionSystemUri(protectionSystem.getId());

                    StringSet protectionId = new StringSet();
                    protectionId.add("" + cg.getCgId());
                    unManagedProtectionSet.putCGInfo(SupportedCGInformation.PROTECTION_ID.toString(), protectionId);

                    newCG = true;
                } else {
                    log.info("Found existing unmanaged protection set for CG: " + cg.getCgName() + ", using "
                            + unManagedProtectionSet.getId().toString());
                }

                unManagedCGsReturnedFromProvider.add(unManagedProtectionSet.getId());

                // Update the fields for the CG
                unManagedProtectionSet.setCgName(cg.getCgName());
                unManagedProtectionSet.setLabel(cg.getCgName());

                // Indicate whether the CG is in a healthy state or not to ingest.
                unManagedProtectionSet.getCGCharacteristics().put(UnManagedProtectionSet.SupportedCGCharacteristics.IS_HEALTHY.name(),
                        cg.getCgState().equals(GetCGStateResponse.HEALTHY) ? Boolean.TRUE.toString() : Boolean.FALSE.toString());

                // Indicate whether the CG is sync or async
                unManagedProtectionSet.getCGCharacteristics().put(UnManagedProtectionSet.SupportedCGCharacteristics.IS_SYNC.name(),
                        cg.getCgPolicy().synchronous ? Boolean.TRUE.toString() : Boolean.FALSE.toString());

                // Fill in RPO type and value information
                StringSet rpoType = new StringSet();
                rpoType.add(cg.getCgPolicy().rpoType);
                unManagedProtectionSet.putCGInfo(SupportedCGInformation.RPO_TYPE.toString(), rpoType);

                StringSet rpoValue = new StringSet();
                rpoValue.add(cg.getCgPolicy().rpoValue.toString());
                unManagedProtectionSet.putCGInfo(SupportedCGInformation.RPO_VALUE.toString(), rpoValue);

                if (null == cg.getCopies()) {
                    log.info("Protection Set " + nativeGuid + " does not contain any copies.  Skipping...");
                    continue;
                }
                if (null == cg.getRsets()) {
                    log.info("Protection Set " + nativeGuid + " does not contain any replication sets.  Skipping...");
                    continue;
                }

                // clean up the existing journal and replicationsets info in the unmanaged protection set, so that updated info is populated
                if (!newCG) {
                    cleanUpUnManagedResources(unManagedProtectionSet, unManagedVolumesToUpdateByWwn, dbClient);
                }

                // Now map UnManagedVolume objects to the journal and rset (sources/targets) and put RP fields in them
                Map<String, String> rpCopyAccessStateMap = new HashMap<String, String>();

                mapCgJournals(unManagedProtectionSet, cg, rpCopyAccessStateMap, rpWwnToNativeWwn, storageNativeIdPrefixes, dbClient);

                mapCgSourceAndTargets(unManagedProtectionSet, cg, rpCopyAccessStateMap, rpWwnToNativeWwn, storageNativeIdPrefixes, dbClient);

                if (newCG) {
                    unManagedCGsInsert.add(unManagedProtectionSet);
                } else {
                    unManagedCGsUpdate.add(unManagedProtectionSet);
                }
            } catch (Exception ex) {
                log.error("Error processing RP CG {}", cg.getCgName(), ex);
            }
        }

        handlePersistence(dbClient, false);
        cleanUp(protectionSystem, dbClient);
    }

    /**
     * Link the target volumes to the passed in source volume
     * 
     * @param unManagedProtectionSet unmanaged protection set
     * @param sourceVolume RP CG source volume
     * @param rset RP CG replication set
     * @param rpWwnToNativeWwn Map of RP volume WWN to native volume WWN - required for XIO but harmless otherwise
     * @param storageNativeIdPrefixes List of XIO systems discovered in ViPR
     * @param dbClient DB client instance
     * @return rpTargetVolumeIds Set of unmanaged target volume ids for the given source volume
     */
    private StringSet linkTargetVolumes(UnManagedProtectionSet unManagedProtectionSet, UnManagedVolume sourceVolume, GetRSetResponse rset,
            Map<String, String> rpWwnToNativeWwn, List<String> storageNativeIdPrefixes, DbClient dbClient) {
        StringSet rpTargetVolumeIds = new StringSet();
        // Find the target volumes associated with this source volume.
        for (GetVolumeResponse targetVolume : rset.getVolumes()) {
            // Find this volume in UnManagedVolumes based on wwn
            UnManagedVolume targetUnManagedVolume = null;
            String targetWwn = rpWwnToNativeWwn.get(targetVolume.getWwn());
            if (targetWwn != null) {
                targetUnManagedVolume = findUnManagedVolumeForWwn(targetWwn, dbClient, storageNativeIdPrefixes);
            }

            if (null == targetUnManagedVolume) {
                log.info("Protection Set {} contains unknown target volume: {}. Skipping.",
                        unManagedProtectionSet.getNativeGuid(), targetVolume.getWwn());
                continue;
            }

            // Don't bother if we just re-found the source device
            if (targetUnManagedVolume.getId().equals(sourceVolume.getId())) {
                continue;
            }

            // Check if this volume is already managed, which would indicate it has already been partially ingested
            Volume targetManagedVolume = DiscoveryUtils.checkManagedVolumeExistsInDBByWwn(dbClient, targetVolume.getWwn());
            if (null != targetManagedVolume) {
                log.info("Protection Set {} has an orphaned unmanaged target volume {}. Skipping.",
                        unManagedProtectionSet.getNativeGuid(), targetUnManagedVolume.getLabel());
                continue;
            }
            log.info("\tfound target volume {}", targetUnManagedVolume.forDisplay());

            // Add the source unmanaged volume ID to the target volume
            StringSet rpUnManagedSourceVolumeId = new StringSet();
            rpUnManagedSourceVolumeId.add(sourceVolume.getId().toString());
            targetUnManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_UNMANAGED_SOURCE_VOLUME.toString(),
                    rpUnManagedSourceVolumeId);

            // Update the target unmanaged volume with the source managed volume ID
            unManagedVolumesToUpdateByWwn.put(targetUnManagedVolume.getWwn(), targetUnManagedVolume);

            // Store the unmanaged target ID in the source volume
            rpTargetVolumeIds.add(targetUnManagedVolume.getId().toString());
        }

        return rpTargetVolumeIds;
    }

    /**
     * Update (if it exists) the source and target UnManagedVolume objects with RP information needed for
     * ingestion
     * 
     * @param unManagedProtectionSet unmanaged protection set
     * @param cg CG response got back from RP system
     * @param rpCopyAccessStateMap Map to hold the access state of the replication sets
     * @param rpWwnToNativeWwn Map of RP volume WWN to native volume WWN - required for XIO but harmless otherwise
     * @param storageNativeIdPrefixes List of XIO systems discovered in ViPR
     * @param dbClient DB client instance
     */
    private void mapCgSourceAndTargets(UnManagedProtectionSet unManagedProtectionSet, GetCGsResponse cg,
            Map<String, String> rpCopyAccessStateMap, Map<String, String> rpWwnToNativeWwn, List<String> storageNativeIdPrefixes,
            DbClient dbClient) {
        for (GetRSetResponse rset : cg.getRsets()) {
            for (GetVolumeResponse volume : rset.getVolumes()) {
                // Find this volume in UnManagedVolumes based on wwn
                UnManagedVolume unManagedVolume = findUnManagedVolumeForWwn(volume.getWwn(), dbClient, storageNativeIdPrefixes);

                // Check if this volume is already managed, which would indicate it has already been partially ingested
                Volume managedVolume = DiscoveryUtils.checkManagedVolumeExistsInDBByWwn(dbClient, volume.getWwn());

                // Add the WWN to the unmanaged protection set, regardless of whether this volume is unmanaged or not.
                unManagedProtectionSet.getVolumeWwns().add(volume.getWwn());

                if (null == unManagedVolume && null == managedVolume) {
                    log.info("Protection Set {} contains unknown Replication Set volume: {}. Skipping.",
                            unManagedProtectionSet.getNativeGuid(), volume.getWwn());
                    continue;
                }

                if (null != managedVolume) {
                    log.info("Protection Set {} contains volume {} that is already managed",
                            unManagedProtectionSet.getNativeGuid(), volume.getWwn());
                    // make sure it's in the UnManagedProtectionSet's ManagedVolume ids
                    if (!unManagedProtectionSet.getManagedVolumeIds().contains(managedVolume.getId().toString())) {
                        unManagedProtectionSet.getManagedVolumeIds().add(managedVolume.getId().toString());
                    }

                    if (!managedVolume.checkInternalFlags(Flag.INTERNAL_OBJECT) && null != unManagedVolume) {
                        log.info("Protection Set {} also has an orphaned UnManagedVolume {} that will be removed",
                                unManagedProtectionSet.getNativeGuid(), unManagedVolume.getLabel());
                        // remove the unManagedVolume from the UnManagedProtectionSet's UnManagedVolume ids
                        unManagedProtectionSet.getUnManagedVolumeIds().remove(unManagedVolume.getId().toString());
                        unManagedVolumesToDelete.add(unManagedVolume);
                        // because this volume is already managed, we can just continue to the next
                        continue;
                    }

                }

                // at this point, we have an legitimate UnManagedVolume whose RP properties should be updated
                log.info("Processing Replication Set UnManagedVolume {}", unManagedVolume.forDisplay());

                // Add the unmanaged volume to the list (if it's not there already)
                if (!unManagedProtectionSet.getUnManagedVolumeIds().contains(unManagedVolume.getId().toString())) {
                    unManagedProtectionSet.getUnManagedVolumeIds().add(unManagedVolume.getId().toString());
                }

                // Update the fields in the UnManagedVolume to reflect RP characteristics
                String personality = Volume.PersonalityTypes.SOURCE.name();
                if (!volume.isProduction()) {
                    personality = Volume.PersonalityTypes.TARGET.name();
                }

                updateCommonRPProperties(unManagedProtectionSet, unManagedVolume, personality, volume, dbClient);

                // Update other RP properties for source/targets
                // What Replication Set does this volume belong to? (so we can associate sources to targets.)
                // What is the access state.
                StringSet rpAccessState = new StringSet();
                rpAccessState.add(rpCopyAccessStateMap.get(volume.getRpCopyName()));
                unManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_ACCESS_STATE.toString(), rpAccessState);
                StringSet rsetName = new StringSet();
                rsetName.add(rset.getName());
                unManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_RSET_NAME.toString(),
                        rsetName);

                rpWwnToNativeWwn.put(volume.getWwn(), unManagedVolume.getWwn());

                unManagedVolumesToUpdateByWwn.put(unManagedVolume.getWwn(), unManagedVolume);
            }

            // Now that we've processed all of the sources and targets, we can mark all of the target devices in the source devices.
            for (GetVolumeResponse volume : rset.getVolumes()) {
                // Only process source volumes here.
                if (!volume.isProduction()) {
                    continue;
                }

                // Find this volume in UnManagedVolumes based on wwn
                // See if the unmanaged volume is in the list of volumes to update
                // (it should be, unless the backing array has not been discovered)

                UnManagedVolume unManagedVolume = null;
                String wwn = rpWwnToNativeWwn.get(volume.getWwn());
                if (wwn != null) {
                    unManagedVolume = findUnManagedVolumeForWwn(wwn, dbClient,
                            storageNativeIdPrefixes);
                }

                if (null == unManagedVolume) {
                    log.info("Protection Set {} contains unknown volume: {}. Skipping.",
                            unManagedProtectionSet.getNativeGuid(), volume.getWwn());
                    continue;
                }

                log.info("Linking target volumes to source volume {}", unManagedVolume.forDisplay());
                StringSet rpTargetVolumeIds = linkTargetVolumes(unManagedProtectionSet, unManagedVolume, rset, rpWwnToNativeWwn,
                        storageNativeIdPrefixes, dbClient);

                // Add the unmanaged target IDs to the source unmanaged volume
                unManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_UNMANAGED_TARGET_VOLUMES.toString(),
                        rpTargetVolumeIds);

                unManagedVolumesToUpdateByWwn.put(unManagedVolume.getWwn(), unManagedVolume);
            }
        }
    }

    /**
     * Update (if it exists) the journal UnManagedVolume objects with RP information needed for
     * ingestion
     * 
     * @param unManagedProtectionSet unmanaged protection set
     * @param cg CG response got back from RP system
     * @param rpCopyAccessStateMap Map to hold the access state of the replication sets.
     * @param rpWwnToNativeWwn Map of RP volume WWN to native volume WWN - required for XIO but harmless otherwise
     * @param storageNativeIdPrefixes List of XIO systems discovered in ViPR
     * @param dbClient DB client instance
     */
    private void mapCgJournals(UnManagedProtectionSet unManagedProtectionSet, GetCGsResponse cg,
            Map<String, String> rpCopyAccessStateMap, Map<String, String> rpWwnToNativeWwn, List<String> storageNativeIdPrefixes,
            DbClient dbClient) {
        for (GetCopyResponse copy : cg.getCopies()) {
            String accessState = copy.getAccessState();
            for (GetVolumeResponse volume : copy.getJournals()) {
                // Find this volume in UnManagedVolumes based on wwn
                UnManagedVolume unManagedVolume = findUnManagedVolumeForWwn(volume.getWwn(), dbClient, storageNativeIdPrefixes);

                // Check if this volume is already managed, which would indicate it has already been partially ingested
                Volume managedVolume = DiscoveryUtils.checkManagedVolumeExistsInDBByWwn(dbClient, volume.getWwn());

                // Add the WWN to the unmanaged protection set, regardless of whether this volume is unmanaged or not.
                unManagedProtectionSet.getVolumeWwns().add(volume.getWwn());

                if (null == unManagedVolume && null == managedVolume) {
                    log.info("Protection Set {} contains unknown Journal volume: {}. Skipping.",
                            unManagedProtectionSet.getNativeGuid(), volume.getWwn());
                    continue;
                }

                if (null != managedVolume) {
                    log.info("Protection Set {} contains volume {} that is already managed",
                            unManagedProtectionSet.getNativeGuid(), volume.getWwn());
                    // make sure it's in the UnManagedProtectionSet's ManagedVolume ids
                    if (!unManagedProtectionSet.getManagedVolumeIds().contains(managedVolume.getId().toString())) {
                        unManagedProtectionSet.getManagedVolumeIds().add(managedVolume.getId().toString());
                    }

                    if (null != unManagedVolume) {
                        log.info("Protection Set {} also has an orphaned UnManagedVolume {} that will be removed",
                                unManagedProtectionSet.getNativeGuid(), unManagedVolume.getLabel());
                        // remove the unManagedVolume from the UnManagedProtectionSet's UnManagedVolume ids
                        unManagedProtectionSet.getUnManagedVolumeIds().remove(unManagedVolume.getId().toString());
                        unManagedVolumesToDelete.add(unManagedVolume);
                    }

                    // because this volume is already managed, we can just continue to the next
                    continue;
                }

                // at this point, we have an legitimate UnManagedVolume whose RP properties should be updated
                log.info("Processing Journal UnManagedVolume {}", unManagedVolume.forDisplay());

                // Capture the access state
                rpCopyAccessStateMap.put(volume.getRpCopyName(), accessState);

                // Add the unmanaged volume to the list (if it's not there already)
                if (!unManagedProtectionSet.getUnManagedVolumeIds().contains(unManagedVolume.getId().toString())) {
                    unManagedProtectionSet.getUnManagedVolumeIds().add(unManagedVolume.getId().toString());
                }

                updateCommonRPProperties(unManagedProtectionSet, unManagedVolume, Volume.PersonalityTypes.METADATA.name(), volume, dbClient);

                rpWwnToNativeWwn.put(volume.getWwn(), unManagedVolume.getWwn());

                unManagedVolumesToUpdateByWwn.put(unManagedVolume.getWwn(), unManagedVolume);
            }
        }

    }

    /**
     * Update the common fields in the UnManagedVolume to reflect RP characteristics
     * Is this volume SOURCE, TARGET, or JOURNAL?
     * What's the RP Copy Name of this volume? (what copy does it belong to?)
     * 
     * @param unManagedProtectionSet
     * @param unManagedVolume
     * @param personalityType
     * @param volume
     * @param dbClient
     */
    private void updateCommonRPProperties(UnManagedProtectionSet unManagedProtectionSet, UnManagedVolume unManagedVolume,
            String personalityType, GetVolumeResponse volume, DbClient dbClient) {
        StringSet personality = new StringSet();
        personality.add(personalityType);
        unManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_PERSONALITY.toString(),
                personality);

        StringSet rpCopyName = new StringSet();
        rpCopyName.add(volume.getRpCopyName());
        unManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_COPY_NAME.toString(),
                rpCopyName);

        StringSet rpInternalSiteName = new StringSet();
        rpInternalSiteName.add(volume.getInternalSiteName());
        unManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_INTERNAL_SITENAME.toString(),
                rpInternalSiteName);

        StringSet rpProtectionSystemId = new StringSet();
        rpProtectionSystemId.add(unManagedProtectionSet.getProtectionSystemUri().toString());
        unManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_PROTECTIONSYSTEM.toString(),
                rpProtectionSystemId);

        // Filter in RP source vpools, filter out everything else (if source)
        // Filter out certain vpools if target/journal

        filterProtectedVpools(dbClient, unManagedVolume, personality.iterator().next());
    }

    /**
     * Clean up the existing unmanaged protection set and its associated unmanaged volumes
     * so that it gets updated with latest info during rediscovery
     * 
     * @param unManagedProtectionSet unmanaged protection set
     * @param unManagedVolumesToUpdateByWwn unmanaged volumes to update
     * @param dbClient db client
     */
    private void cleanUpUnManagedResources(UnManagedProtectionSet unManagedProtectionSet,
            Map<String, UnManagedVolume> unManagedVolumesToUpdateByWwn, DbClient dbClient) {
        // Clean up the volume wwns, managed volume and unmanaged volume lists of the unmanaged protection set
        unManagedProtectionSet.getManagedVolumeIds().clear();
        unManagedProtectionSet.getVolumeWwns().clear();
        List<URI> unManagedVolsUris = new ArrayList<URI>(Collections2.transform(
                unManagedProtectionSet.getUnManagedVolumeIds(), CommonTransformerFunctions.FCTN_STRING_TO_URI));
        Iterator<UnManagedVolume> unManagedVolsOfProtectionSetIter = dbClient.queryIterativeObjects(UnManagedVolume.class,
                unManagedVolsUris);
        while (unManagedVolsOfProtectionSetIter.hasNext()) {
            UnManagedVolume rpUnManagedVolume = unManagedVolsOfProtectionSetIter.next();
            // Clear the RP related fields in the UnManagedVolume
            StringSet rpPersonality = rpUnManagedVolume.getVolumeInformation().get(SupportedVolumeInformation.RP_PERSONALITY.toString());
            StringSet rpCopyName = rpUnManagedVolume.getVolumeInformation().get(SupportedVolumeInformation.RP_COPY_NAME.toString());
            StringSet rpInternalSiteName = rpUnManagedVolume.getVolumeInformation().get(
                    SupportedVolumeInformation.RP_INTERNAL_SITENAME.toString());
            StringSet rpProtectionSystem = rpUnManagedVolume.getVolumeInformation()
                    .get(SupportedVolumeInformation.RP_PROTECTIONSYSTEM.toString());
            StringSet rpSourceVol = rpUnManagedVolume.getVolumeInformation()
                    .get(SupportedVolumeInformation.RP_UNMANAGED_SOURCE_VOLUME.toString());
            StringSet rpTargetVols = rpUnManagedVolume.getVolumeInformation()
                    .get(SupportedVolumeInformation.RP_UNMANAGED_TARGET_VOLUMES.toString());
            StringSet rpAccessState = rpUnManagedVolume.getVolumeInformation()
                    .get(SupportedVolumeInformation.RP_ACCESS_STATE.toString());

            if (rpPersonality != null) {
                rpPersonality.clear();
            }
            if (rpCopyName != null) {
                rpCopyName.clear();
            }
            if (rpInternalSiteName != null) {
                rpInternalSiteName.clear();
            }
            if (rpProtectionSystem != null) {
                rpProtectionSystem.clear();
            }
            if (rpSourceVol != null) {
                rpSourceVol.clear();
            }
            if (rpTargetVols != null) {
                rpTargetVols.clear();
            }
            if (rpAccessState != null) {
                rpAccessState.clear();
            }
            unManagedVolumesToUpdateByWwn.put(rpUnManagedVolume.getWwn(), rpUnManagedVolume);
        }

        unManagedProtectionSet.getUnManagedVolumeIds().clear();
    }

    /**
     * Filter vpools from the qualified list.
     * rpSource true: Filter out anything other than RP source vpools
     * rpSource false: Filter out RP and SRDF source vpools
     * 
     * @param dbClient dbclient
     * @param unManagedVolume unmanaged volume
     * @param personality SOURCE, TARGET, or METADATA
     */
    private void filterProtectedVpools(DbClient dbClient, UnManagedVolume unManagedVolume, String personality) {

        if (unManagedVolume.getSupportedVpoolUris() != null && !unManagedVolume.getSupportedVpoolUris().isEmpty()) {
            Iterator<VirtualPool> vpoolItr = dbClient.queryIterativeObjects(VirtualPool.class,
                    URIUtil.toURIList(unManagedVolume.getSupportedVpoolUris()));
            while (vpoolItr.hasNext()) {
                boolean remove = false;
                VirtualPool vpool = vpoolItr.next();

                // If this is an SRDF source vpool, we can filter out since we're dealing with an RP volume
                if (vpool.getProtectionRemoteCopySettings() != null) {
                    remove = true;
                }

                // If this is not an RP source, the vpool should be filtered out if:
                // The vpool is an RP vpool (has settings) and target vpools are non-null
                if (vpool.getProtectionVarraySettings() != null && ((Volume.PersonalityTypes.TARGET.name().equalsIgnoreCase(personality)) ||
                        Volume.PersonalityTypes.METADATA.name().equalsIgnoreCase(personality))) {
                    boolean foundEmptyTargetVpool = false;
                    Map<URI, VpoolProtectionVarraySettings> settings = VirtualPool.getProtectionSettings(vpool, dbClient);
                    for (Map.Entry<URI, VpoolProtectionVarraySettings> setting : settings.entrySet()) {
                        if (setting.getValue().getVirtualPool() == null) {
                            foundEmptyTargetVpool = true;
                            break;
                        }
                    }

                    // If this is a journal volume, also check the journal vpools. If they're not set, we cannot filter out this vpool.
                    if (Volume.PersonalityTypes.METADATA.name().equalsIgnoreCase(personality) &&
                            (vpool.getJournalVpool() == null || vpool.getStandbyJournalVpool() == null)) {
                        foundEmptyTargetVpool = true;
                    }

                    // If every relevant target (and journal for journal volumes) vpool is filled-in, then
                    // you would never assign your target volume to this source vpool, so filter it out.
                    if (!foundEmptyTargetVpool) {
                        remove = true;
                    }
                }
                
                if (Volume.PersonalityTypes.SOURCE.name().equalsIgnoreCase(personality)) {                    
                    if (!VirtualPool.vPoolSpecifiesProtection(vpool) ) {
                        // If this an RP source, the vpool must be an RP vpool
                        remove = true;
                    } else if (unManagedVolume.getVolumeInformation().containsKey(
                                SupportedVolumeInformation.RP_STANDBY_INTERNAL_SITENAME.toString())
                                && !VirtualPool.vPoolSpecifiesMetroPoint(vpool)) {
                        // Since this is a Source volume with the presence of RP_STANDBY_INTERNAL_SITENAME 
                        // it indicates that this volume is MetroPoint, if we get here, this is vpool
                        // must be filtered out since it's not MP.
                        remove = true;
                    }
                }

                if (remove) {
                    log.info("Removing virtual pool " + vpool.getLabel() + " from supported vpools for unmanaged volume: "
                            + unManagedVolume.getLabel());
                    unManagedVolume.getSupportedVpoolUris().remove(vpool.getId().toString());
                }
            }
        }
    }

    /**
     * Find an UnManagedVolume for the given WWN by first checking in the
     * UnManagedVolumesToUpdate collection (in case we've already fetched it
     * and updated it elsewhere), and then check the database.
     * 
     * @param wwn the WWN to find an UnManagedVolume for
     * @param dbClient a reference to the database client
     * @param cachedStorageNativeIds see comments, cached list of storage native GUIDs
     * @return an UnManagedVolume object for the given WWN
     */
    private UnManagedVolume findUnManagedVolumeForWwn(String wwn, DbClient dbClient, List<String> cachedStorageNativeIds) {
        UnManagedVolume unManagedVolume = unManagedVolumesToUpdateByWwn.get(wwn);

        if (null == unManagedVolume) {
            unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDBByWwn(dbClient, wwn);
        }

        // Special for RP. XIO unmanaged volumes store a WWN in the "wwn" field that will not match
        // the WWN returned by RP, however the proper 128-but WWN is in two places:
        // 1. The volume information "NATIVE_ID" field. (not indexable, so hard to run a query to find)
        // 2. Locked in the native guid of the volume XTREMIO+APM00144755987+UNMANAGEDVOLUME+616a8770e89749a7908d48a3dd9cf0fd
        // The goal of this section of code is to loop through XIO arrays and search for the native guid
        // based on that XIO native guid and wwn to see if we find the unmanaged volume.
        //
        // Someday RP will return the short WWN in the CG information and this inefficient code can be removed.
        if (null == unManagedVolume && cachedStorageNativeIds != null) {
            for (String storageNativeIdPrefix : cachedStorageNativeIds) {
                // Search for the unmanaged volume based on the native GUID
                String searchCriteria = storageNativeIdPrefix + "+UNMANAGEDVOLUME+" + wwn.toLowerCase();
                List<UnManagedVolume> volumes = CustomQueryUtility.getUnManagedVolumeByNativeGuid(dbClient, searchCriteria);
                if (volumes != null && !volumes.isEmpty()) {
                    log.info("Found XIO unmanaged volume: " + volumes.get(0).getLabel());
                    return volumes.get(0);
                }
            }
        }

        return unManagedVolume;
    }

    /**
     * Handle updating the database with UnManagedProtectionSets,
     * and also clearing out any orphaned ingested UnManagedVolumes.
     * Unless the flush argument is true, only when the set to be persisted
     * reaches the value of BATCH_SIZE will the database be updated.
     * 
     * @param dbClient a reference to the database client
     * @param flush if true, all changes will be persisted regardless
     *            of batch size status
     */
    private void handlePersistence(DbClient dbClient, boolean flush) {
        if (null != unManagedCGsInsert) {
            if (flush || (unManagedCGsInsert.size() > BATCH_SIZE)) {
                partitionManager.insertInBatches(unManagedCGsInsert,
                        BATCH_SIZE, dbClient, UNMANAGED_PROTECTION_SET);
                unManagedCGsInsert.clear();
            }
        }
        if (null != unManagedCGsUpdate) {
            if (flush || (unManagedCGsUpdate.size() > BATCH_SIZE)) {
                partitionManager.updateAndReIndexInBatches(unManagedCGsUpdate,
                        BATCH_SIZE, dbClient, UNMANAGED_PROTECTION_SET);
                unManagedCGsUpdate.clear();
            }
        }
        if (null != unManagedVolumesToUpdateByWwn) {
            if (flush || (unManagedVolumesToUpdateByWwn.size() > BATCH_SIZE)) {
                partitionManager.updateAndReIndexInBatches(
                        new ArrayList<UnManagedVolume>(unManagedVolumesToUpdateByWwn.values()),
                        BATCH_SIZE, dbClient, UNMANAGED_VOLUME);
                unManagedVolumesToUpdateByWwn.clear();
            }
        }
        if (null != unManagedVolumesToDelete) {
            if (flush || (unManagedVolumesToDelete.size() > BATCH_SIZE)) {
                dbClient.markForDeletion(unManagedVolumesToDelete);
                unManagedVolumesToDelete.clear();
            }
        }
    }

    /**
     * Flushes the rest of the UnManagedProtectionSet changes to the database
     * and cleans up (i.e., removes) any UnManagedProtectionSets that no longer
     * exist on the RecoverPoint device, but are still in the database.
     * 
     * @param protectionSystem the ProtectionSystem to clean up
     * @param dbClient a reference to the database client
     */
    private void cleanUp(ProtectionSystem protectionSystem, DbClient dbClient) {

        // flush all remaining changes to the database
        handlePersistence(dbClient, true);

        // remove any UnManagedProtectionSets found in the database
        // but no longer found on the RecoverPoint device
        Set<URI> umpsetsFoundInDbForProtectionSystem =
                DiscoveryUtils.getAllUnManagedProtectionSetsForSystem(
                        dbClient, protectionSystem.getId().toString());

        SetView<URI> onlyFoundInDb =
                Sets.difference(umpsetsFoundInDbForProtectionSystem, unManagedCGsReturnedFromProvider);

        if (onlyFoundInDb != null && !onlyFoundInDb.isEmpty()) {
            Iterator<UnManagedProtectionSet> umpsesToDelete =
                    dbClient.queryIterativeObjects(UnManagedProtectionSet.class, onlyFoundInDb, true);
            while (umpsesToDelete.hasNext()) {
                UnManagedProtectionSet umps = umpsesToDelete.next();
                log.info("Deleting orphaned UnManagedProtectionSet {} no longer found on RecoverPoint device.",
                        umps.getNativeGuid());
                dbClient.markForDeletion(umps);
            }
        }

        // reset all tracking collections
        unManagedCGsInsert = null;
        unManagedCGsUpdate = null;
        unManagedVolumesToDelete = null;
        unManagedVolumesToUpdateByWwn = null;
        unManagedCGsReturnedFromProvider = null;
    }
}

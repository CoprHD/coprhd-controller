/*
 * Copyright (c) 2014 EMC Corporation
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

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet.SupportedCGInformation;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
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
     * Discovers the RP CGs and all the volumes therein.  It updates/creates the UnManagedProtectionSet
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
        
        for (GetCGsResponse cg : cgs) {
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
                log.info("Found existing unmanaged protection set for CG: " + cg.getCgName() + ", using " + unManagedProtectionSet.getId().toString());
            }

            unManagedCGsReturnedFromProvider.add(unManagedProtectionSet.getId());

            // Update the fields for the CG
            unManagedProtectionSet.setCgName(cg.getCgName());
            unManagedProtectionSet.setLabel(cg.getCgName());

            // Indicate whether the CG is in a healthy state or not to ingest.
            unManagedProtectionSet.getCGCharacteristics().put(UnManagedProtectionSet.SupportedCGCharacteristics.IS_HEALTHY.name(), 
                    cg.cgState.equals(GetCGStateResponse.HEALTHY) ? Boolean.TRUE.toString() : Boolean.FALSE.toString());

            // Indicate whether the CG is sync or async
            unManagedProtectionSet.getCGCharacteristics().put(UnManagedProtectionSet.SupportedCGCharacteristics.IS_SYNC.name(), 
                    cg.cgPolicy.synchronous ? Boolean.TRUE.toString() : Boolean.FALSE.toString());
            
            // Fill in RPO type and value information
            StringSet rpoType = new StringSet();
            rpoType.add(cg.cgPolicy.rpoType);
            unManagedProtectionSet.putCGInfo(SupportedCGInformation.RPO_TYPE.toString(), rpoType);

            StringSet rpoValue = new StringSet();
            rpoValue.add(cg.cgPolicy.rpoValue.toString());
            unManagedProtectionSet.putCGInfo(SupportedCGInformation.RPO_VALUE.toString(), rpoValue);
            
            // Now map UnManagedVolume objects to the journal and rset (sources/targets) and put RP fields in them
            if (null == cg.getCopies()) {
                log.info("Protection Set " + nativeGuid + " does not contain any copies.  Skipping...");
                continue;
            }

            for (GetCopyResponse copy : cg.getCopies()) {
                for (GetVolumeResponse volume : copy.getJournals()) {
                    // Find this volume in UnManagedVolumes based on wwn
                    UnManagedVolume unManagedVolume = findUnManagedVolumeForWwn(volume.getWwn(), dbClient);
                    
                    // Check if this volume is already managed, which would indicate it has already been partially ingested
                    Volume managedVolume = DiscoveryUtils.checkManagedVolumeExistsInDBByWwn(dbClient, volume.getWwn());
                    
                    // Add the WWN to the unmanaged protection set, regardless of whether this volume is unmanaged or not.
                    unManagedProtectionSet.getVolumeWwns().add(volume.getWwn());
                    
                    if (null == unManagedVolume && null == managedVolume) {
                        log.info("Protection Set {} contains unknown Journal volume: {}. Skipping.", 
                                nativeGuid, volume.getWwn());
                        continue;
                    }
                    
                    if (null != managedVolume) {
                        log.info("Protection Set {} contains volume {} that is already managed", 
                                nativeGuid, volume.getWwn());
                        // make sure it's in the UnManagedProtectionSet's ManagedVolume ids
                        if (!unManagedProtectionSet.getManagedVolumeIds().contains(managedVolume.getId())) {
                            unManagedProtectionSet.getManagedVolumeIds().add(managedVolume.getId().toString());
                        }
                        
                        if (null != unManagedVolume) {
                            log.info("Protection Set {} also has an orphaned UnManagedVolume {} that will be removed",
                                    nativeGuid, unManagedVolume.getLabel());
                            // remove the unManagedVolume from the UnManagedProtectionSet's UnManagedVolume ids
                            unManagedProtectionSet.getUnManagedVolumeIds().remove(unManagedVolume.getId());
                            unManagedVolumesToDelete.add(unManagedVolume);
                        }
                        
                        // because this volume is already managed, we can just continue to the next
                        continue;
                    }
                    
                    // at this point, we have an legitimate UnManagedVolume whose RP properties should be updated
                    log.info("Processing Journal UnManagedVolume {}", unManagedVolume.forDisplay());
                    
                    // Add the unmanaged volume to the list (if it's not there already)
                    if (!unManagedProtectionSet.getUnManagedVolumeIds().contains(unManagedVolume.getId())) {
                        unManagedProtectionSet.getUnManagedVolumeIds().add(unManagedVolume.getId().toString());
                    }
                    
                    // Update the fields in the UnManagedVolume to reflect RP characteristics
                    // Is this volume SOURCE, TARGET, or JOURNAL?
                    // What's the RP Copy Name of this volume? (what copy does it belong to?)
                    // What Replication Set does this volume belong to?  (so we can associate sources to targets.  Does not apply to JOURNALS)
                    StringSet personality = new StringSet();
                    personality.add(Volume.PersonalityTypes.METADATA.name());
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
                    rpProtectionSystemId.add(protectionSystem.getId().toString());
                    unManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_PROTECTIONSYSTEM.toString(),
                            rpProtectionSystemId);                    

                    // Filter out RP and SRDF source vpools since this is a journal volume
                    filterProtectedVpools(dbClient, unManagedVolume, personality.iterator().next());
                    
                    unManagedVolumesToUpdateByWwn.put(unManagedVolume.getWwn(), unManagedVolume);
                }
            }
            
            if (null == cg.getRsets()) {
                log.info("Protection Set " + nativeGuid + " does not contain any replication sets.  Skipping...");
                continue;
            }

            for (GetRSetResponse rset : cg.getRsets()) {
                for (GetVolumeResponse volume : rset.getVolumes()) {
                    // Find this volume in UnManagedVolumes based on wwn
                    UnManagedVolume unManagedVolume = findUnManagedVolumeForWwn(volume.getWwn(), dbClient);

                    // Check if this volume is already managed, which would indicate it has already been partially ingested
                    Volume managedVolume = DiscoveryUtils.checkManagedVolumeExistsInDBByWwn(dbClient, volume.getWwn());

                    // Add the WWN to the unmanaged protection set, regardless of whether this volume is unmanaged or not.
                    unManagedProtectionSet.getVolumeWwns().add(volume.getWwn());

                    if (null == unManagedVolume && null == managedVolume) {
                        log.info("Protection Set {} contains unknown Replication Set volume: {}. Skipping.", 
                                nativeGuid, volume.getWwn());
                        continue;
                    }

                    if (null != managedVolume) {
                        log.info("Protection Set {} contains volume {} that is already managed", 
                                nativeGuid, volume.getWwn());
                        // make sure it's in the UnManagedProtectionSet's ManagedVolume ids
                        if (!unManagedProtectionSet.getManagedVolumeIds().contains(managedVolume.getId())) {
                            unManagedProtectionSet.getManagedVolumeIds().add(managedVolume.getId().toString());
                        }
                        
                        if (null != unManagedVolume) {
                            log.info("Protection Set {} also has an orphaned UnManagedVolume {} that will be removed",
                                    nativeGuid, unManagedVolume.getLabel());
                            // remove the unManagedVolume from the UnManagedProtectionSet's UnManagedVolume ids
                            unManagedProtectionSet.getUnManagedVolumeIds().remove(unManagedVolume.getId());
                            unManagedVolumesToDelete.add(unManagedVolume);
                        }
                        
                        // because this volume is already managed, we can just continue to the next
                        continue;
                    }

                    // at this point, we have an legitimate UnManagedVolume whose RP properties should be updated
                    log.info("Processing Replication Set UnManagedVolume {}", unManagedVolume.forDisplay());

                    // Add the unmanaged volume to the list (if it's not there already)
                    if (!unManagedProtectionSet.getUnManagedVolumeIds().contains(unManagedVolume.getId())) {
                        unManagedProtectionSet.getUnManagedVolumeIds().add(unManagedVolume.getId().toString());
                    }

                    // Update the fields in the UnManagedVolume to reflect RP characteristics
                    // Is this volume SOURCE, TARGET, or JOURNAL?
                    // What's the RP Copy Name of this volume? (what copy does it belong to?)
                    // What Replication Set does this volume belong to?  (so we can associate sources to targets.)
                    StringSet personality = new StringSet();
                    if (volume.isProduction()) {
                        personality.add(Volume.PersonalityTypes.SOURCE.name());
                    } else {
                        personality.add(Volume.PersonalityTypes.TARGET.name());
                    }
                    unManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_PERSONALITY.toString(),
                            personality);

                    StringSet rpCopyName = new StringSet();
                    rpCopyName.add(volume.getRpCopyName());
                    unManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_COPY_NAME.toString(),
                            rpCopyName);

                    StringSet rsetName = new StringSet();
                    rsetName.add(rset.getName());
                    unManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_RSET_NAME.toString(),
                            rsetName);
                    
                    StringSet rpInternalSiteName = new StringSet();
                    rpInternalSiteName.add(volume.getInternalSiteName());
                    unManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_INTERNAL_SITENAME.toString(),
                            rpInternalSiteName);

                    StringSet rpProtectionSystemId = new StringSet();
                    rpProtectionSystemId.add(protectionSystem.getId().toString());
                    unManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_PROTECTIONSYSTEM.toString(),
                            rpProtectionSystemId);

                    // Filter in RP source vpools, filter out everything else (if source)
                    // Filter out certain vpools if target
                    filterProtectedVpools(dbClient, unManagedVolume, personality.iterator().next());
                    
                    unManagedVolumesToUpdateByWwn.put(unManagedVolume.getWwn(), unManagedVolume);
                }

                // Now that we've processed all of the sources and targets, we can mark all of the target devices in the source devices.
                for (GetVolumeResponse volume : rset.getVolumes()) {
                    // Find this volume in UnManagedVolumes based on wwn
                    StringSet rpTargetVolumeIds = new StringSet();
                    UnManagedVolume unManagedVolume = findUnManagedVolumeForWwn(volume.getWwn(), dbClient);
                    
                    if (null == unManagedVolume) {
                        log.info("Protection Set {} contains unknown volume: {}. Skipping.", 
                                nativeGuid, volume.getWwn());
                        continue;
                    }
                    
                    // Only process source volumes here.
                    if (!volume.isProduction()) {
                        continue;
                    }
                    
                    log.info("Linking target volumes to source volume {}", unManagedVolume.forDisplay());
                    
                    // Find the target volumes associated with this source volume.
                    for (GetVolumeResponse targetVolume : rset.getVolumes()) {
                        // Find this volume in UnManagedVolumes based on wwn
                        UnManagedVolume targetUnManagedVolume = findUnManagedVolumeForWwn(targetVolume.getWwn(), dbClient);
                        
                        if (null == targetUnManagedVolume) {
                            log.info("Protection Set {} contains unknown target volume: {}. Skipping.", 
                                    nativeGuid, targetVolume.getWwn());
                            continue;
                        }
                        
                        // Don't bother if we just re-found the source device (TODO: Is this an issue for RP MP where there are two sources?)
                        if (targetUnManagedVolume.getId().equals(unManagedVolume.getId())) {
                            continue;
                        }
                        
                        log.info("\tfound target volume {}", targetUnManagedVolume.forDisplay());
                        
                        // Add the source unmanaged volume ID to the target volume
                        StringSet rpUnManagedSourceVolumeId = new StringSet();
                        rpUnManagedSourceVolumeId.add(unManagedVolume.getId().toString());
                        targetUnManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_UNMANAGED_SOURCE_VOLUME.toString(),
                                rpUnManagedSourceVolumeId);

                        // Update the target unmanaged volume with the source managed volume ID
                        unManagedVolumesToUpdateByWwn.put(targetUnManagedVolume.getWwn(), targetUnManagedVolume);

                        // Store the unmanaged target ID in the source volume
                        rpTargetVolumeIds.add(targetUnManagedVolume.getId().toString());
                    }

                    // Add the unmanaged target IDs to the source unmanaged volume
                    unManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_UNMANAGED_TARGET_VOLUMES.toString(),
                            rpTargetVolumeIds);
                    
                    unManagedVolumesToUpdateByWwn.put(unManagedVolume.getWwn(), unManagedVolume);
                }
            }

            if (newCG) {
                unManagedCGsInsert.add(unManagedProtectionSet);
            } else {
                unManagedCGsUpdate.add(unManagedProtectionSet);
            }

            handlePersistence(dbClient, false);
        }

        cleanUp(protectionSystem, dbClient);
    }

    /**
     * Filter vpools from the qualified list.
     * rpSource true: Filter out anything other than RP source vpools
     * rpSource false: Filter out RP and SRDF source vpools
     * 
     * @param dbClient dbclient
     * @param unManagedVolume unmanaged volume
     * @param rpSource is this volume an RP source?
     */
    private void filterProtectedVpools(DbClient dbClient, UnManagedVolume unManagedVolume, String personality) {
        
        if (unManagedVolume.getSupportedVpoolUris() != null && !unManagedVolume.getSupportedVpoolUris().isEmpty()) {
            Iterator<VirtualPool> vpoolItr = dbClient.queryIterativeObjects(VirtualPool.class, URIUtil.toURIList(unManagedVolume.getSupportedVpoolUris()));
            while (vpoolItr.hasNext()) {
                boolean remove = false;
                VirtualPool vpool = vpoolItr.next();
                
                // If this is an SRDF source vpool, we can filter out since we're dealing with an RP volume
                if (vpool.getProtectionRemoteCopySettings() != null) {
                    remove = true;
                }
                
                // If this is not an RP source, the vpool should be filtered out if:
                // The vpool is an RP vpool (has settings) and target vpools are non-null
                if (vpool.getProtectionVarraySettings() != null && (personality.equalsIgnoreCase(Volume.PersonalityTypes.TARGET.name()) || 
                                                                    personality.equalsIgnoreCase(Volume.PersonalityTypes.METADATA.name()))) {
                    boolean foundEmptyTargetVpool = false;
                    Map<URI, VpoolProtectionVarraySettings> settings = VirtualPool.getProtectionSettings(vpool, dbClient);
                    for (Map.Entry<URI, VpoolProtectionVarraySettings> setting : settings.entrySet()) {
                        if (setting.getValue().getVirtualPool() == null) {
                            foundEmptyTargetVpool = true;
                            break;
                        }
                    }
                    
                    // If this is a journal volume, also check the journal vpools.  If they're not set, we cannot filter out this vpool. 
                    if (personality.equalsIgnoreCase(Volume.PersonalityTypes.METADATA.name()) &&
                        (vpool.getJournalVpool() == null || vpool.getStandbyJournalVpool() == null)) {
                       foundEmptyTargetVpool = true;
                    }   
                    
                    // If every relevant target (and journal for journal volumes) vpool is filled-in, then
                    // you would never assign your target volume to this source vpool, so filter it out.
                    if (!foundEmptyTargetVpool) {
                        remove = true;
                    }
                }
                
                // If this an RP source, the vpool must be an RP vpool
                if (vpool.getProtectionVarraySettings() == null && personality.equalsIgnoreCase((Volume.PersonalityTypes.SOURCE.name()))) {                        
                    remove = true;
                }
                
                if (remove) {
                    log.info("Removing virtual pool " + vpool.getLabel() + " from supported vpools for unmanaged volume: " + unManagedVolume.getLabel());
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
     * 
     * @return an UnManagedVolume object for the given WWN
     */
    private UnManagedVolume findUnManagedVolumeForWwn(String wwn, DbClient dbClient) {
        UnManagedVolume unManagedVolume = unManagedVolumesToUpdateByWwn.get(wwn);

        if (null == unManagedVolume) {
            unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDBByWwn(dbClient, wwn);
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
     *              of batch size status
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
        Set<URI> umpsesFoundInDbForProtectionSystem = 
                DiscoveryUtils.getAllUnManagedProtectionSetsForSystem(
                        dbClient, protectionSystem.getId().toString());

        SetView<URI> onlyFoundInDb = 
                Sets.difference(umpsesFoundInDbForProtectionSystem, unManagedCGsReturnedFromProvider);

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

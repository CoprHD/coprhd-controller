/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.recoverpoint;

import java.net.URI;
import java.util.ArrayList;
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
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.recoverpoint.impl.RecoverPointClient;
import com.emc.storageos.recoverpoint.responses.GetCGsResponse;
import com.emc.storageos.recoverpoint.responses.GetCopyResponse;
import com.emc.storageos.recoverpoint.responses.GetRSetResponse;
import com.emc.storageos.recoverpoint.responses.GetVolumeResponse;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;

public class RPUnManagedObjectDiscoverer {

    private static final Logger log = LoggerFactory.getLogger(RPUnManagedObjectDiscoverer.class);
    public static final String UNMANAGED_CG = "UnManagedCG";

    List<UnManagedProtectionSet> unManagedCGsInsert = null;
    List<UnManagedProtectionSet> unManagedCGsUpdate = null;
    Set<URI> unManagedCGsReturnedFromProvider = new HashSet<URI>();

    /**
     * Discovers the RP CGs and all the volumes therein.  It updates/creates the UnManagedProtectionSet
     * objects and updates (if it exists) the UnManagedVolume objects with RP information needed for 
     * ingestion
     * 
     * @param accessProfile access profile
     * @param dbClient db client 
     * @param partitionManager partition manager (remove?)
     * @throws Exception
     */
    public void discoverUnManagedObjects(AccessProfile accessProfile, DbClient dbClient,
            PartitionManager partitionManager) throws Exception {

        log.info("Started discovery of UnManagedVolumes for system {}", accessProfile.getSystemId());
        ProtectionSystem protectionSystem = dbClient.queryObject(ProtectionSystem.class, accessProfile.getSystemId());
        RecoverPointClient rp = RPHelper.getRecoverPointClient(protectionSystem);

        unManagedCGsInsert = new ArrayList<UnManagedProtectionSet>();
        unManagedCGsUpdate = new ArrayList<UnManagedProtectionSet>();
        
        // Get all of the consistency groups (and their volumes) from RP
        Set<GetCGsResponse> cgs = rp.getAllCGs();
        
        if (cgs == null) {
            log.warn("No CGs were found on protection system: " + protectionSystem.getLabel());
            return;            
        }
        
        for (GetCGsResponse cg : cgs) {
            log.info("Processing returned CG: " + cg.getCgName());
            boolean newCG = false;

            // Not the best UID hash ever.  Really should use CG UID
            String nativeGuid = protectionSystem.getNativeGuid() + "+" + cg.getCgName();

            // First check to see if this protection set is already part of our managed DB
            if (null != DiscoveryUtils.checkProtectionSetExistsInDB(dbClient, nativeGuid)) {
                log.info("Protection Set " + nativeGuid + " already is managed by ViPR, skipping unmanaged discovery");
                continue;
            }

            // Now check to see if the unmanaged CG exists in the database
            UnManagedProtectionSet unManagedProtectionSet = DiscoveryUtils.checkUnManagedProtectionSetExistsInDB(dbClient, nativeGuid);

            // TODO: Only update if something actually changed from what's in the DB
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

            // Update the fields for the CG
            unManagedProtectionSet.setCgName(cg.getCgName());

            // TODO: Fill in these values with reality
            unManagedProtectionSet.getCGCharacteristics().put(UnManagedProtectionSet.SupportedCGCharacteristics.IS_ENABLED.name(), Boolean.TRUE.toString());

            
            // Now map UnManagedVolume objects to the journal and rset (sources/targets) and put RP fields in them
            if (null == cg.getCopies()) {
                log.info("Protection Set " + nativeGuid + " does not contain any copies.  Skipping...");
                continue;
            }
            
            for (GetCopyResponse copy : cg.getCopies()) {
                for (GetVolumeResponse volume : copy.getJournals()) {
                    // Find this volume in UnManagedVolumes based on wwn
                    UnManagedVolume unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDBByWwn(dbClient, volume.getWwn());
                    
                    // Add the WWN to the unmanaged protection set, regardless of whether this volume is unmanaged or not.
                    unManagedProtectionSet.getVolumeWwns().add(volume.getWwn());
                    
                    if (null == unManagedVolume) {
                        log.info("Protection Set " + nativeGuid + " contains volume: " + volume.getWwn() + " that is not in our database of unmanaged volumes.  Skipping.");
                        continue;                        
                    }
                    
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
                    
                    dbClient.updateObject(unManagedVolume);
                }
            }
            
            if (null == cg.getRsets()) {
                log.info("Protection Set " + nativeGuid + " does not contain any replication sets.  Skipping...");
                continue;
            }

            for (GetRSetResponse rset : cg.getRsets()) {
                for (GetVolumeResponse volume : rset.getVolumes()) {
                    // Find this volume in UnManagedVolumes based on wwn
                    UnManagedVolume unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDBByWwn(dbClient, volume.getWwn());

                    // Add the WWN to the unmanaged protection set, regardless of whether this volume is unmanaged or not.
                    unManagedProtectionSet.getVolumeWwns().add(volume.getWwn());
                    
                    if (null == unManagedVolume) {
                        log.info("Protection Set " + nativeGuid + " contains volume: " + volume.getWwn() + " that is not in our database of unmanaged volumes.  Skipping.");
                        continue;                        
                    }
                    
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
                    
                    dbClient.updateObject(unManagedVolume);
                }                    

                // Now that we've processed all of the sources and targets, we can mark all of the target devices in the source devices.
                for (GetVolumeResponse volume : rset.getVolumes()) {
                    // Find this volume in UnManagedVolumes based on wwn
                    StringSet rpTargetVolumeIds = new StringSet();
                    UnManagedVolume unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDBByWwn(dbClient, volume.getWwn());
                    
                    if (null == unManagedVolume) {
                        log.info("Protection Set " + nativeGuid + " contains volume: " + volume.getWwn() + " that is not in our database of unmanaged volumes.  Skipping.");
                        continue;                        
                    }
                    
                    // Only process source volumes here.
                    if (!volume.isProduction()) {
                        continue;
                    }
                    
                    // Find the target volumes associated with this source volume.
                    for (GetVolumeResponse targetVolume : rset.getVolumes()) {
                        // Find this volume in UnManagedVolumes based on wwn
                        UnManagedVolume targetUnManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDBByWwn(dbClient, targetVolume.getWwn());
                        
                        if (null == targetUnManagedVolume) {
                            log.info("Protection Set " + nativeGuid + " contains volume: " + targetVolume.getWwn() + " that is not in our database of unmanaged volumes (target search).  Skipping.");
                            continue;                        
                        }
                        
                        // Don't bother if we just re-found the source device (TODO: Is this an issue for RP MP where there are two sources?)
                        if (targetUnManagedVolume.getId().equals(unManagedVolume.getId())) {
                            continue;
                        }
                        
                        // Add the source unmanaged volume ID to the target volume
                        StringSet rpUnManagedSourceVolumeId = new StringSet();
                        rpUnManagedSourceVolumeId.add(unManagedVolume.getId().toString());
                        targetUnManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_UNMANAGED_SOURCE_VOLUME.toString(),
                                rpUnManagedSourceVolumeId);                        

                        // Update the target unmanaged volume with the source managed volume ID
                        dbClient.updateObject(targetUnManagedVolume);

                        // Store the unmanaged target ID in the source volume
                        rpTargetVolumeIds.add(targetUnManagedVolume.getId().toString());
                    }

                    // Add the unmanaged target IDs to the source unmanaged volume
                    unManagedVolume.putVolumeInfo(SupportedVolumeInformation.RP_UNMANAGED_TARGET_VOLUMES.toString(),
                            rpTargetVolumeIds);
                    
                    dbClient.updateObject(unManagedVolume);
                }

            }

            if (newCG) {
                dbClient.createObject(unManagedProtectionSet);
            } else {
                dbClient.updateObject(unManagedProtectionSet);
            }
        }
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
}

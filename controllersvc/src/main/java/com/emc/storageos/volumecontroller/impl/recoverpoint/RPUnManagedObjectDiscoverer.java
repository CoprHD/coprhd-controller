/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.recoverpoint;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet;
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
                unManagedProtectionSet = new UnManagedProtectionSet();
                unManagedProtectionSet.setId(URIUtil.createId(UnManagedVolume.class));
                unManagedProtectionSet.setNativeGuid(nativeGuid);
                newCG = true;
            }

            // Update the fields for the CG
            unManagedProtectionSet.setCgName(cg.getCgName());

            // TODO: Fill in these values with reality
            unManagedProtectionSet.getCGCharacteristics().put(UnManagedProtectionSet.SupportedCGCharacteristics.IS_ENABLED.name(), Boolean.TRUE.toString());

            if (newCG) {
                dbClient.persistObject(unManagedProtectionSet);
            } else {
                dbClient.updateAndReindexObject(unManagedProtectionSet);
            }
            
            // Now map UnManagedVolume objects to the journal and rset (sources/targets) and put RP fields in them
            if (null == cg.getCopies()) {
                log.info("Protection Set " + nativeGuid + " does not contain any copies.  Skipping...");
                continue;
            }
            
            for (GetCopyResponse copy : cg.getCopies()) {
                for (GetVolumeResponse volume : copy.getJournals()) {
                    // Find this volume in UnManagedVolumes based on wwn
                    UnManagedVolume unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDBByWwn(dbClient, volume.getWwn());
                    
                    if (null == unManagedVolume) {
                        log.info("Protection Set " + nativeGuid + " contains volume: " + volume.getWwn() + " that is not in our database of unmanaged volumes.  Skipping.");
                        continue;                        
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
                    
                    dbClient.updateAndReindexObject(unManagedVolume);
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
                    
                    if (null == unManagedVolume) {
                        log.info("Protection Set " + nativeGuid + " contains volume: " + volume.getWwn() + " that is not in our database of unmanaged volumes.  Skipping.");
                        continue;                        
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
                    
                    dbClient.updateAndReindexObject(unManagedVolume);
                }                    
            }
        }
    }
}

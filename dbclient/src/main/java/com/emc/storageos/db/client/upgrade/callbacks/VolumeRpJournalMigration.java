/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to initialize RecoverPoint BlockConsistencyGroups,
 * RecoverPoint VirtualPools, and RecoverPoint source/target Volume journal
 * references.
 * 
 */
public class VolumeRpJournalMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(VolumeRpJournalMigration.class);

    private static String RP_SRC_JOURNAL_APPEND = "-journal-prod";
    private static String RP_TGT_JOURNAL_APPEND = "-target-journal-";
    private static String RP_JOURNAL = "-journal";

    @Override
    public void process() throws MigrationCallbackException {
        updateVolumeRpJournalRefs();
    }

    /**
     * For all RP source/target volumes, identify the associated journal volumes
     * and add the reference.
     */
    private void updateVolumeRpJournalRefs() {
        log.info("Updating RecoverPoint journal references for source/target volumes.");
        DbClient dbClient = this.getDbClient();
        List<URI> protectionSetURIs = dbClient.queryByType(ProtectionSet.class, false);

        Iterator<ProtectionSet> protectionSets =
                dbClient.queryIterativeObjects(ProtectionSet.class, protectionSetURIs);
        Map<String, List<Volume>> replicationSetVolumes = null;
        String replicationSetName = null;
        while (protectionSets.hasNext()) {
            replicationSetVolumes = new HashMap<String, List<Volume>>();
            ProtectionSet ps = protectionSets.next();

            log.info("Examining ProtectionSet (id={}) for upgrade", ps.getId().toString());

            // Organize the volumes by replication set
            for (String protectionVolumeID : ps.getVolumes()) {
                URI uri = URI.create(protectionVolumeID);
                Volume protectionVolume = dbClient.queryObject(Volume.class, uri);
                replicationSetName = protectionVolume.getRSetName();

                if (replicationSetName != null) {
                    if (replicationSetVolumes.get(replicationSetName) == null) {
                        List<Volume> volumes = new ArrayList<Volume>();
                        replicationSetVolumes.put(replicationSetName, volumes);
                    }
                    replicationSetVolumes.get(replicationSetName).add(protectionVolume);
                }
            }

            // For each replication set we need to determine what source or target volume
            // each journal belongs to.
            for (String replicationSet : replicationSetVolumes.keySet()) {
                log.info("Examining RecoverPoint volumes in Replication Set '{}' for upgrade", replicationSet);
                List<Volume> rSetVolumes = replicationSetVolumes.get(replicationSet);

                for (Volume rSetVolume : rSetVolumes) {
                    if (isNonJournalVolume(rSetVolume)) {
                        Volume journalVolume = getAssociatedJournalVolume(rSetVolume, ps.getVolumes());
                        if (journalVolume != null) {
                            log.info("Updated Volume (id={}) to reference journal Volume (id={})",
                                    rSetVolume.getLabel(), journalVolume.getLabel());
                            rSetVolume.setRpJournalVolume(journalVolume.getId());
                            dbClient.persistObject(rSetVolume);
                        }
                    }
                }
            }
        }
    }

    /**
     * Finds the associated journal volume for a source or target RP volume.
     * 
     * @param srcTgtVolume
     * @param volumes
     * @return
     */
    private Volume getAssociatedJournalVolume(Volume srcTgtVolume, StringSet protectionSetVolumes) {
        if (isNonJournalVolume(srcTgtVolume)) {
            Iterator<String> volumeURIs = protectionSetVolumes.iterator();
            while (volumeURIs.hasNext()) {
                Volume volume = dbClient.queryObject(Volume.class, URI.create(volumeURIs.next()));
                // Only consider the protection set volumes that are journals (METADATA)
                if (volume.getPersonality().equalsIgnoreCase(
                        Volume.PersonalityTypes.METADATA.toString())) {
                    // Find a source/journal match based on an exact volume label match. Find the target/journal
                    // match based on a volume label match once we remove '-journal' from the journal volume.
                    if (srcTgtVolume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.SOURCE.toString())
                            && volume.getLabel().equals(srcTgtVolume.getLabel() + RP_SRC_JOURNAL_APPEND)) {
                        return volume;
                    } else if (srcTgtVolume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.TARGET.toString())
                            && volume.getLabel().contains(RP_TGT_JOURNAL_APPEND)) {
                        if (volume.getLabel().replace(RP_JOURNAL, "").equals(srcTgtVolume.getLabel())) {
                            return volume;
                        }
                    }

                }
            }
        }

        return null;
    }

    /**
     * Determines if a volume is a non journal (source or target)
     * 
     * @param volume
     * @return
     */
    private boolean isNonJournalVolume(Volume volume) {
        return volume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.SOURCE.toString())
                || volume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.TARGET.toString());
    }
}

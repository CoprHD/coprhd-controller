/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;

/**
 * Migration handler to remove the BlockConsistencyGroup reference from all RP Journal volumes and make sure the protection set reference is
 * set for all journal volumes
 * 
 */
public class VolumeRpJournalCGFix extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(VolumeRpJournalCGFix.class);

    @Override
    public void process() {
        log.info("Updating RecoverPoint journal volumes to fix BlockConsistencyGroup and ProtectionSet references");
        DbClient dbClient = this.getDbClient();
        List<URI> cgUris = dbClient.queryByType(BlockConsistencyGroup.class, false);

        Iterator<BlockConsistencyGroup> cgs = dbClient.queryIterativeObjects(BlockConsistencyGroup.class, cgUris);
        List<Volume> volsToUpdate = new ArrayList<Volume>();
        while (cgs.hasNext()) {
            updateJournalsForCg(volsToUpdate, cgs.next());
        }
        log.info(String.format("Updating %d volumes for all RP CGs", volsToUpdate.size()));
        dbClient.updateObject(volsToUpdate);
    }

    /**
     * update all journal volumes in one CG to null out the consistencyGroup field and update the protectionSet field if necessary
     * 
     * @param volsToUpdate
     *            a running list of volumes to update later on
     * @param cg
     *            cg to update journals for
     */
    private void updateJournalsForCg(List<Volume> volsToUpdate, BlockConsistencyGroup cg) {
        try {
            if (cg.checkForType(BlockConsistencyGroup.Types.RP)) {
                log.info(String.format("Found RP consistency group %s", nameAndId(cg)));

                // get volumes for this cg
                List<Volume> volsForCg = RPHelper.getCgVolumes(cg.getId(), this.getDbClient());
                if (volsForCg.isEmpty()) {
                    // this cg has no volumes; nothing to update
                    log.info(String.format("skipping RP CG %s because it contains no volumes", nameAndId(cg)));
                    return;
                }

                // get the protection set
                URI protSet = getProtectionSetId(volsForCg);

                if (protSet == null) {
                    // something is wrong with this cg; we won't touch it
                    log.info(String.format("skipping RP CG %s; it has volumes but none with a protection set setting", nameAndId(cg)));
                    return;
                }

                // loop through the journal volumes and set cg id to null and protection set to the right value
                for (Volume vol : volsForCg) {
                    updateJournalVolume(volsToUpdate, protSet, vol);
                }
            }
        } catch (Exception e) {
            // log the message and return; we want to continue and update the rest of the cgs even if we get an exception
            log.error(e.getMessage(), e);
        }
    }

    /**
     * update consistencyGroup and protectionSet fields for one volume if it is a journal volume also update associated volumes if the
     * journal volume is a vplex virtual volume
     * 
     * @param volsToUpdate
     *            updated volume is added to this list
     * @param protSetId
     *            protection set id
     * @param vol
     *            volume to update
     */
    private void updateJournalVolume(List<Volume> volsToUpdate, URI protSetId, Volume vol) {
        try {
            if (Volume.PersonalityTypes.METADATA.toString().equals(vol.getPersonality())) {

                String logMsg = String.format("Updating journal volume %s; changing cg from %s to null", nameAndId(vol), vol.getConsistencyGroup());

                if (NullColumnValueGetter.isNullNamedURI(vol.getProtectionSet())) {
                    vol.setProtectionSet(new NamedURI(protSetId, vol.getLabel()));
                    logMsg += String.format(" and protectionSet from null to %s", protSetId);
                }

                vol.setConsistencyGroup(NullColumnValueGetter.getNullURI());

                log.info(logMsg);
                volsToUpdate.add(vol);

                // look for associated volumes and update those as well (means this journal volume is a vplex virtual volume)
                if (vol.getAssociatedVolumes() != null || !vol.getAssociatedVolumes().isEmpty()) {
                    for (String volId : vol.getAssociatedVolumes()) {
                        Volume assocVol = getDbClient().queryObject(Volume.class, URI.create(volId));
                        if (assocVol != null && !assocVol.getInactive()) {
                            assocVol.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                            log.info(String.format("Updating backing journal volume volume %s; changing cg from %s to null", nameAndId(assocVol),
                                    assocVol.getConsistencyGroup()));
                            volsToUpdate.add(assocVol);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // log the message and return; we want to continue and update the rest of the journal volumes even if we get an exception
            log.error(e.getMessage(), e);
        }
    }

    /**
     * convenience method to return label and id for logging
     * 
     * @param obj
     * @return
     */
    private String nameAndId(DataObject obj) {
        return String.format("%s (id=%s)", obj.getLabel(), obj.getId().toString());
    }

    /**
     * get the protection set associated with a list of volumes in a CG by looping through the list and returning the first protection set
     * id found
     * 
     * @param volsForCg
     *            a list of volumes in one RP CG
     * @return the protection set id or null if none is found
     */
    private URI getProtectionSetId(List<Volume> volsForCg) {
        for (Volume vol : volsForCg) {
            if (!NullColumnValueGetter.isNullNamedURI(vol.getProtectionSet())) {
                return vol.getProtectionSet().getURI();
            }
        }
        return null;
    }
}

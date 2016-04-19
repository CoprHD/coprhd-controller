/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to initialize the new DataObject.internalFlags field
 * 
 * The two sub-classes which this is presently needed are FileShare and Volume
 * and both must be handled by this single callback.
 * 
 */
public class DataObjectInternalFlagsInitializer extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(DataObjectInternalFlagsInitializer.class);

    @Override
    public void process() throws MigrationCallbackException {
        updateFlagsAndProjectForInternalFileShares();
        updateInternalVolumes();
    }

    /**
     * Convert old-style internal object FileShares (with project == null)
     * into new-style ones with the constant URN and internal flags
     */
    private void updateFlagsAndProjectForInternalFileShares() {
        DbClient dbClient = this.getDbClient();
        List<URI> fileShareKeys = dbClient.queryByType(FileShare.class, false);

        Iterator<FileShare> fileShareObjs =
                dbClient.queryIterativeObjects(FileShare.class, fileShareKeys);
        while (fileShareObjs.hasNext()) {
            FileShare fs = fileShareObjs.next();
            log.debug("Examining FileShare (id={}) for upgrade", fs.getId().toString());
            if (fs.getProject() == null) {
                fs.setProject(new NamedURI(FileShare.INTERNAL_OBJECT_PROJECT_URN, fs.getLabel()));
                fs.addInternalFlags(Flag.INTERNAL_OBJECT, Flag.NO_PUBLIC_ACCESS, Flag.NO_METERING);
                dbClient.updateAndReindexObject(fs);
                log.info("Converted internal FileShare (id={}) to use internal flags", fs.getId().toString());
            }
        }
    }

    /**
     * Update volumes that need to have the INTERNAL_OBJECT flag set.
     */
    private void updateInternalVolumes() {
        DbClient dbClient = getDbClient();
        List<URI> volumeURIs = dbClient.queryByType(Volume.class, false);
        Iterator<Volume> volumes = dbClient.queryIterativeObjects(Volume.class, volumeURIs);
        while (volumes.hasNext()) {
            Volume volume = volumes.next();
            log.debug("Examining volume (id={}) for upgrade", volume.getId().toString());

            // Check if the volume has associated volumes. If so,
            // this is a VPLEX volume, and we must mark these
            // associated backend volumes as internal.
            StringSet associatedVolumeIds = volume.getAssociatedVolumes();
            if ((associatedVolumeIds != null) && (!associatedVolumeIds.isEmpty())) {
                log.info("Backend volumes for VPLEX volume (id={}) must be upgraded", volume.getId().toString());
                handleVPlexAssociatedVolumes(associatedVolumeIds);
                continue;
            }

            // Check to see if the personality of the volume is of type "METADATA" if so, this is an
            // RP Journal volume and should be marked as internal.
            if (volume.getPersonality() != null && volume.getPersonality().equals(Volume.PersonalityTypes.METADATA.toString())) {
                log.info("RecoverPoint Journal volume (id={}) must be upgraded", volume.getId().toString());
                volume.addInternalFlags(Flag.INTERNAL_OBJECT);
                volume.addInternalFlags(Flag.SUPPORTS_FORCE);
                dbClient.persistObject(volume);
                log.info("Marked RecoverPoint Journal volume (id={}) as internal object that supports force", volume.getId().toString());
            }
        }
    }

    /**
     * Sets the INTERNAL_OBJECT flag for the passed associated backend volumes
     * of a VPLEX volume.
     * 
     * @param associatedVolumes The backend volumes of a VPLEX volume in the
     *            database.
     */
    private void handleVPlexAssociatedVolumes(StringSet associatedVolumeIds) {
        List<URI> associatedVolumeURIs = StringSetUtil
                .stringSetToUriList(associatedVolumeIds);
        List<Volume> associatedVolumes = dbClient.queryObject(Volume.class,
                associatedVolumeURIs);
        Iterator<Volume> associatedVolumesIter = associatedVolumes.iterator();
        while (associatedVolumesIter.hasNext()) {
            Volume associatedVolume = associatedVolumesIter.next();
            associatedVolume.addInternalFlags(Flag.INTERNAL_OBJECT);
            log.info("Marked VPLEX backend volume (id={}) as internal object", associatedVolume.getId().toString());
        }
        dbClient.persistObject(associatedVolumes);
    }
}

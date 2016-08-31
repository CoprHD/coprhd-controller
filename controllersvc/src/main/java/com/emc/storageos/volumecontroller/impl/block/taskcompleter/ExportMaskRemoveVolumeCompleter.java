/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.google.common.base.Joiner;

public class ExportMaskRemoveVolumeCompleter extends ExportTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(ExportMaskRemoveVolumeCompleter.class);

    private Collection<URI> _volumes;

    /**
     * Constructor for ExportMaskRemoveVolumeCompleter.
     * 
     * @param egUri -- ExportGroup URI
     * @param emUri -- ExportMask URI
     * @param volumes -- List<URI> of volumes being removed.
     * @param task -- API task id.
     */
    public ExportMaskRemoveVolumeCompleter(URI egUri, URI emUri, Collection<URI> volumes,
            String task) {
        super(ExportGroup.class, egUri, emUri, task);
        _volumes = new ArrayList<URI>();
        _volumes.addAll(volumes);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            ExportMask exportMask = (getMask() != null) ?
                    dbClient.queryObject(ExportMask.class, getMask()) : null;
            for (URI volumeURI : _volumes) {
                BlockObject volume = BlockObject.fetch(dbClient, volumeURI);
                if (exportMask != null && status == Operation.Status.ready) {
                    exportMask.removeFromUserCreatedVolumes(volume);
                    exportMask.removeVolume(volume.getId());
                }
            }

            if (exportMask != null) {
                if (exportMask.getVolumes() == null ||
                        exportMask.getVolumes().isEmpty()) {
                    exportGroup.removeExportMask(exportMask.getId());
                    dbClient.markForDeletion(exportMask);
                    dbClient.updateObject(exportGroup);
                } else {
                    dbClient.updateObject(exportMask);
                }
            }

            removeVolumesFromExportGroup(dbClient, exportGroup);

            _log.info(String.format(
                    "Done ExportMaskRemoveVolume - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));
        } catch (Exception e) {
            _log.error(String.format(
                    "Failed updating status for ExportMaskRemoveVolume - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    /**
     * Routine examines the passed in ExportGroup to see if any of the BlockObject URIs
     * referenced in its _volumes are referenced in any of its associated ExportMasks.
     * If it is not, then this completer should clean up references to the volumes in
     * the ExportGroup.
     * 
     * @param dbClient [in] - DbClient Object used for accessing DB
     * @param exportGroup [in] - ExportGroup object to examine and update
     */
    private void removeVolumesFromExportGroup(DbClient dbClient, ExportGroup exportGroup) {
        // Populate a a set of volumes that should be removed and a set of
        // volumes that were found to be in one of the associated ExportMasks
        Set<URI> copyOfVolumes = new HashSet<>(_volumes); // Initially have all the volumes
        Set<URI> volumesInAnExportMask = new HashSet<>();
        List<URI> exportMaskURIs = StringSetUtil.stringSetToUriList(exportGroup.getExportMasks());
        List<ExportMask> exportMasks = dbClient.queryObject(ExportMask.class, exportMaskURIs, true);
        for (ExportMask associatedMask : exportMasks) {
            if (associatedMask.getVolumes() != null) {
                for (URI volumeURI : ExportMaskUtils.getVolumeURIs(associatedMask)) {
                    if (_volumes.contains(volumeURI)) {
                        volumesInAnExportMask.add(volumeURI);
                        // Remove the volumes from _volumes copy
                        copyOfVolumes.remove(volumeURI);
                    }
                }
            }
        }

        if (!volumesInAnExportMask.isEmpty()) {
            _log.info(String.
                    format("The following volumes are in an ExportMask associated with ExportGroup %s (%s): %s",
                            exportGroup.getLabel(), exportGroup.getId(), Joiner.on(',').join(volumesInAnExportMask)));
        }

        // Anything that's remaining in the copy is a volume that was not
        // found in any of the associated ExportMasks.
        if (!copyOfVolumes.isEmpty()) {
            for (URI uri : copyOfVolumes) {
                exportGroup.removeVolume(uri);
            }
            dbClient.updateObject(exportGroup);
            _log.info(String.
                    format("The following volumes were removed from ExportGroup %s (%s): %s",
                            exportGroup.getLabel(), exportGroup.getId(),
                            Joiner.on(',').join(copyOfVolumes)));
        }
    }
}

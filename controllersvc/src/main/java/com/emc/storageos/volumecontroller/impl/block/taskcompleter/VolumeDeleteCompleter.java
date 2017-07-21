/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.NamedURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;

public class VolumeDeleteCompleter extends VolumeTaskCompleter {
    private static final Logger _log = LoggerFactory
            .getLogger(VolumeDeleteCompleter.class);

    public VolumeDeleteCompleter(URI volUri, String task) {
        super(Volume.class, volUri, task);
    }

    public VolumeDeleteCompleter(List<URI> volUris, String task) {
        super(Volume.class, volUris, task);
    }

    /**
     * Remove reference of deleted volume from associated source volume and associated host
     * 
     * @param dbClient
     * @param deletedVolume
     */
    private void removeDeletedVolumeReference(DbClient dbClient, Volume deletedVolume) {
        if (deletedVolume != null && !NullColumnValueGetter.isNullURI(deletedVolume.getAssociatedSourceVolume())) {
            Volume srcVolume = dbClient.queryObject(Volume.class, deletedVolume.getAssociatedSourceVolume());

            // remove reference of deleted volume from fullCopies
            if (srcVolume != null) {
                srcVolume.getFullCopies().remove(deletedVolume.getId().toString());
                dbClient.persistObject(srcVolume);
            }
        }
        
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            List<Volume> volumes = dbClient.queryObject(Volume.class, getIds());
            for (Volume volume : volumes) {
                switch (status) {
                    case error:
                        // Pattern for rollback quality:
                        // If the step is a rollback step and we did receive an error, we want to notify the
                        // user of the potential resource(s) that were not cleaned-up as a by-product of this
                        // failure, so we will add such information into the incoming service code message.
                        if (isRollingBack() && (coded instanceof ServiceError)) {
                            ServiceError error = (ServiceError) coded;
                            String originalMessage = error.getMessage();
                            String additionMessage = "Rollback encountered problems cleaning up " +
                                    volume.getNativeGuid() + " and may require manual clean up";
                            String updatedMessage = String.format("%s\n%s", originalMessage, additionMessage);
                            error.setMessage(updatedMessage);
                        }

                        // if SRDF Protected Volume, then change it to a normal device.
                        // in case of array locks, target volume deletions fail some times.
                        // This fix, converts a RDF device to non-rdf device in ViPr, so that this volume is exposed to UI for deletion again.
                        if (volume.checkForSRDF()) {
                            volume.setPersonality(NullColumnValueGetter.getNullStr());
                            volume.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                            volume.setLinkStatus(NullColumnValueGetter.getNullStr());
                            if (!NullColumnValueGetter.isNullNamedURI(volume.getSrdfParent())) {
                                volume.setSrdfParent(new NamedURI(NullColumnValueGetter.getNullURI(), NullColumnValueGetter.getNullStr()));
                                volume.setSrdfCopyMode(NullColumnValueGetter.getNullStr());
                                volume.setSrdfGroup(NullColumnValueGetter.getNullURI());
                            } else if (null != volume.getSrdfTargets()) {
                                volume.getSrdfTargets().clear();
                            }
                        }
                        dbClient.updateObject(volume);

                        dbClient.error(Volume.class, volume.getId(), getOpId(), coded);
                        break;
                    default:
                        dbClient.ready(Volume.class, volume.getId(), getOpId());
                }

                _log.info(String.format("Done VolumeDelete - Id: %s, OpId: %s, status: %s",
                        getId().toString(), getOpId(), status.name()));
                // Generate Zero Metering Record only after successful deletion
                if (Operation.Status.ready == status) {
                    if (null != volume) {
                        removeDeletedVolumeReference(dbClient, volume);
                    }
                }

                recordBlockVolumeOperation(dbClient, OperationTypeEnum.DELETE_BLOCK_VOLUME, status, volume.getId().toString());
            }

            if (status.equals(Operation.Status.ready) || (status.equals(Operation.Status.error) && isRollingBack())) {
                for (Volume volume : volumes) {
                    volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                }
                dbClient.markForDeletion(volumes);
            }

        } catch (Exception e) {
            _log.error(String.format(
                    "Failed updating status for VolumeDelete - Id: %s, OpId: %s", getIds()
                            .toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }
}

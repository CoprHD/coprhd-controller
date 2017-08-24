/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

import java.net.URI;
import java.util.List;

import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SRDFSwapCompleter extends SRDFTaskCompleter {

    private static final Logger _log = LoggerFactory.getLogger(SRDFSwapCompleter.class);
    private Volume.LinkStatus successLinkStatus;

    public enum SwapPhase {
        NONE, FAILED_OVER, SWAPPED, RESUMED
    }
    private SwapPhase lastSwapPhase = SwapPhase.NONE;

    public SRDFSwapCompleter(List<URI> ids, String opId, Volume.LinkStatus successLinkStatus) {
        super(ids, opId);
        this.successLinkStatus = successLinkStatus;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            setDbClient(dbClient);
            recordSRDFOperation(dbClient, OperationTypeEnum.SWAP_SRDF_VOLUME, status, getTargetVolume().getId().toString(),
                    getSourceVolume().getId().toString());

            if (status.equals(Status.error)) {

                Volume.LinkStatus linkStatusOnError = null;

                switch (lastSwapPhase) {
                    case NONE:
                        // Nothing to do.
                        break;
                    case FAILED_OVER:
                        appendMessage(coded, "Volumes were failed over, but not swapped.  Please retry the operation.");
                        linkStatusOnError = Volume.LinkStatus.FAILED_OVER;
                        break;
                    case SWAPPED:
                        appendMessage(coded, "Volumes were swapped and may require manually resuming.");
                        linkStatusOnError = Volume.LinkStatus.SUSPENDED;
                        break;
                    case RESUMED:
                        // Nothing to do.
                        break;
                }

                // Handle partial failure and update volumes accordingly.
                for (Volume volume : getVolumes()) {
                    if (linkStatusOnError != null) {
                        volume.setLinkStatus(linkStatusOnError.name());
                    }
                    volume.setAccessState(getVolumeAccessStateForSuccess(volume).name());
                }
                dbClient.updateObject(getVolumes());
            }
        } catch (Exception e) {
            _log.error("Failed updating status. SRDF Volume Swap {}, for task " + getOpId(), getId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    @Override
    protected Volume.LinkStatus getVolumeSRDFLinkStatusForSuccess() {
        // Link status returned depends on whether link was already swapped.
        // See SRDFDeviceController.
        return successLinkStatus;
    }

    public SwapPhase getLastSwapPhase() {
        return lastSwapPhase;
    }

    public void setLastSwapPhase(SwapPhase lastSwapPhase) {
        this.lastSwapPhase = lastSwapPhase;
    }

    private void appendMessage(ServiceCoded coded, String message) {
        _log.warn(message);
        if (coded instanceof ServiceError) {
            String originalMessage = coded.getMessage();
            String updatedMessage = String.format("%s\n%s", originalMessage, message);
            ((ServiceError) coded).setMessage(updatedMessage);
        }
    }
}

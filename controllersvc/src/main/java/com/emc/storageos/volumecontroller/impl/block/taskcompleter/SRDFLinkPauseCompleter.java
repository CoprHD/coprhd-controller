/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.Volume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.smis.SRDFOperations.Mode;

public class SRDFLinkPauseCompleter extends SRDFTaskCompleter {

    private static final Logger _log = LoggerFactory.getLogger(SRDFLinkPauseCompleter.class);

    public SRDFLinkPauseCompleter(List<URI> ids, String opId) {
        super(ids, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            setDbClient(dbClient);
            recordSRDFOperation(dbClient, OperationTypeEnum.PAUSE_SRDF_LINK, status, getSourceVolume().getId().toString(),
                    getTargetVolume().getId().toString());
        } catch (Exception e) {
            _log.error("Failed updating status. SRDFLinkPause {}, for task " + getOpId(), getId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    @Override
    protected Volume.LinkStatus getVolumeSRDFLinkStatusForSuccess() {
        return Volume.LinkStatus.SPLIT;
    }

    @Override
    protected Volume.VolumeAccessState getVolumeAccessStateForSuccess(Volume v) {
    	if (null != v.getSrdfCopyMode() && Mode.ACTIVE.equals(Mode.valueOf(v.getSrdfCopyMode())) && v.getPersonality().equals(Volume.PersonalityTypes.TARGET.toString())) {
    		// For Active mode target access state is always updated from the provider
    		// after each operation so just use that.
    		return Volume.VolumeAccessState.getVolumeAccessState(v.getAccessState());
    	} else {
    		return Volume.VolumeAccessState.READWRITE;
    	}
    }
}

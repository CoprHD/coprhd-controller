/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class MirrorFileStartTaskCompleter extends MirrorFileTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(MirrorFileStartTaskCompleter.class);
    public MirrorFileStartTaskCompleter(Class clazz, List<URI> ids, String opId) {
        super(clazz, ids, opId);
        // TODO Auto-generated constructor stub
    }
    

    public MirrorFileStartTaskCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
        // TODO Auto-generated constructor stub
    }

    public MirrorFileStartTaskCompleter(URI sourceURI, URI targetURI, String opId) {
        super(sourceURI, targetURI, opId);
        // TODO Auto-generated constructor stub
    }
    
   
    
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded)
            throws DeviceControllerException {
        try {
            setDbClient(dbClient);

            switch (status) {

                case ready:

                    if (null != srcVolumes && null != tgtVolumes && !srcVolumes.isEmpty() && !tgtVolumes.isEmpty()) {
                        for (Volume sourceVol : srcVolumes) {
                            sourceVol.setPersonality(NullColumnValueGetter.getNullStr());
                            sourceVol.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                            if (null != sourceVol.getSrdfTargets()) {
                                sourceVol.getSrdfTargets().clear();
                            }
                            sourceVol.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                            dbClient.persistObject(sourceVol);
                        }

                        for (Volume target : tgtVolumes) {
                            target.setPersonality(NullColumnValueGetter.getNullStr());
                            target.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                            target.setSrdfParent(new NamedURI(NullColumnValueGetter.getNullURI(), NullColumnValueGetter.getNullStr()));
                            target.setSrdfCopyMode(NullColumnValueGetter.getNullStr());
                            target.setSrdfGroup(NullColumnValueGetter.getNullURI());
                            target.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                            dbClient.updateAndReindexObject(target);
                        }

                        Volume target = tgtVolumes.iterator().next();
                        Volume source = srcVolumes.iterator().next();
                        _log.info("SRDF Devices source {} and target {} converted to non srdf devices", source.getId(),
                                target.getId());
                        recordMirrorOperation(dbClient, OperationTypeEnum.START_FILE_MIRROR, status, getSourceFileShare().getId().toString(),
                                getTargetFileShare().getId().toString());
                    }
                default:
                    _log.info("Unable to handle SRDF Link Stop Operational status: {}", status);
            }

        } catch (Exception e) {
            _log.error("Failed updating status. SRDFMirrorStop {}, for task " + getOpId(), getId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }
}

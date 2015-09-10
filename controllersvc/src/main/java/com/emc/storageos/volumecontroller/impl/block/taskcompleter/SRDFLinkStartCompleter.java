/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class SRDFLinkStartCompleter extends SRDFTaskCompleter {

    private static final Logger _log = LoggerFactory.getLogger(SRDFLinkStartCompleter.class);

    private String sourceRepGroup;

    private String targetRepGroup;

    private URI sourceCGUri;

    public SRDFLinkStartCompleter(List<URI> ids, String opId) {
        super(ids, opId);
    }

    public void setCGName(final String sourceGroupName, final String targetGroupName,
            final URI sourceCGUri) {
        this.sourceRepGroup = sourceGroupName;
        this.targetRepGroup = targetGroupName;
        this.sourceCGUri = sourceCGUri;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        _log.info("Completing with status: {}", status);
        try {
            setDbClient(dbClient);

            switch (status) {

                case ready:
                    Volume target = getTargetVolume();
                    // Pin the target System with the source CG, which helps to identify this system is
                    // a target R2 for CG.
                    if (null != sourceCGUri) {
                        URI targetSystemUri = target.getStorageController();
                        StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class,
                                targetSystemUri);
                        if (targetSystem.getTargetCgs() == null) {
                            targetSystem.setTargetCgs(new StringSet());
                        }
                        targetSystem.getTargetCgs().add(sourceCGUri.toString());
                        dbClient.persistObject(targetSystem);
                    }

                    RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class,
                            target.getSrdfGroup());
                    group.setSourceReplicationGroupName(sourceRepGroup);
                    group.setTargetReplicationGroupName(targetRepGroup);
                    dbClient.persistObject(group);
                    break;

                default:
                    _log.info("Unable to handle status: {}", status);
            }
            recordSRDFOperation(dbClient, OperationTypeEnum.CREATE_SRDF_LINK, status, getSourceVolume().getId().toString(),
                    getTargetVolume().getId().toString());
        } catch (Exception e) {
            _log.error("Failed updating status. SRDFLinkStart {}, for task " + getOpId(), getId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    @Override
    protected Volume.LinkStatus getVolumeSRDFLinkStatusForSuccess() {
        return Volume.LinkStatus.IN_SYNC;
    }
}

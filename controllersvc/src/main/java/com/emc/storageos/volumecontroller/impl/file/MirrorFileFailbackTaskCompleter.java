/*
 * Copyright (c) 2015-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.FileShare.MirrorStatus;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class MirrorFileFailbackTaskCompleter extends MirrorFileTaskCompleter {

    private static final Logger _log = LoggerFactory.getLogger(MirrorFileFailbackTaskCompleter.class);

    public MirrorFileFailbackTaskCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            setDbClient(dbClient);
            recordMirrorOperation(dbClient, OperationTypeEnum.FAILBACK_FILE_MIRROR, status, getId());
        } catch (Exception e) {
            _log.error("Failed updating status. MirrorSessionFailback {}, for task " + getOpId(), getId(), e);
        } finally {
            if (Operation.Status.error.equals(status)) {
                FileShare fileshare = dbClient.queryObject(FileShare.class, getId());
                fileshare.setMirrorStatus(MirrorStatus.FAILED_OVER.name());
                dbClient.updateObject(fileshare);
            }
            super.complete(dbClient, status, coded);
        }
    }

    @Override
    protected String getFileMirrorStatusForSuccess(FileShare fs) {
        return MirrorStatus.SYNCHRONIZED.name();
    }

}

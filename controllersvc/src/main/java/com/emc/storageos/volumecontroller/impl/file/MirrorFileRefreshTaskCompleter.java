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
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class MirrorFileRefreshTaskCompleter extends MirrorFileTaskCompleter {

    private static final long serialVersionUID = 1L;
    private static final Logger _log = LoggerFactory.getLogger(MirrorFileRefreshTaskCompleter.class);

    public MirrorFileRefreshTaskCompleter(Class<?> clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            setDbClient(dbClient);
            recordMirrorOperation(dbClient, OperationTypeEnum.REFRESH_FILE_MIRROR, status, getSourceFileShare().getId().toString());
        } catch (Exception e) {
            _log.error("Failed updating status. MirrorSessionRefresh {}, for task " + getOpId(), getId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    @Override
    protected FileShare.MirrorStatus getFileMirrorStatusForSuccess() {
        return this.mirrorSyncStatus;
    }

    public void setFileMirrorStatusForSuccess(FileShare.MirrorStatus mirrorStatus) {
        this.mirrorSyncStatus = mirrorStatus;
    }
}

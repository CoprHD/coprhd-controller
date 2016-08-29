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

import static java.util.Arrays.asList;

/**
 * Created by bonduj on 8/18/2016.
 */
public class MirrorFileCancelTaskCompleter extends MirrorFileTaskCompleter {
    private static final long serialVersionUID = 1L;
    private static final Logger _log = LoggerFactory.getLogger(MirrorFileCancelTaskCompleter.class);

    public MirrorFileCancelTaskCompleter(Class clazz, URI id, String opId, URI storageUri) {
        super(clazz, asList(id), opId, storageUri);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            setDbClient(dbClient);
            recordMirrorOperation(dbClient, OperationTypeEnum.CANCEL_FILE_MIRROR, status, getSourceFileShare().getId().toString());
        } catch (Exception e) {
            _log.error("Failed updating status. MirrorSessionCancel {}, for task " + getOpId(), getId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    @Override
    protected String getFileMirrorStatusForSuccess(FileShare fs) {
        return FileShare.MirrorStatus.UNKNOWN.name();
    }


}

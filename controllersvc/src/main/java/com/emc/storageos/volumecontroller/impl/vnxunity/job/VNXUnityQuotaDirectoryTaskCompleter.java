/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxunity.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class VNXUnityQuotaDirectoryTaskCompleter extends TaskCompleter {
    /**
     * 
     */
    private static final long serialVersionUID = -824046280916742975L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXUnityQuotaDirectoryTaskCompleter.class);

    public VNXUnityQuotaDirectoryTaskCompleter(Class clazz, URI quotaId, String opId) {
        super(clazz, quotaId, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {

        QuotaDirectory quota = dbClient.queryObject(QuotaDirectory.class, getId());
        FileShare fsObj = dbClient.queryObject(FileShare.class, quota.getParent());
        switch (status) {
            case error:
                dbClient.error(QuotaDirectory.class, getId(), getOpId(), coded);
                if (fsObj != null) {
                    dbClient.error(FileShare.class, fsObj.getId(), getOpId(), coded);
                }
                break;
            default:
                dbClient.ready(QuotaDirectory.class, getId(), getOpId());
                if (fsObj != null) {
                    dbClient.ready(FileShare.class, fsObj.getId(), getOpId());
                }
        }

        _logger.info("Done Quota operation {}, with Status: {}", getOpId(), status.name());

    }

}

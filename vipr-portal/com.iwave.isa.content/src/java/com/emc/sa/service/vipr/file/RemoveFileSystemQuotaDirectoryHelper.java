/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.QUOTA_DIRECTORIES;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.QuotaDirectoryRestRep;

public class RemoveFileSystemQuotaDirectoryHelper {
    @Param(QUOTA_DIRECTORIES)
    protected List<String> quotaDirectoryIds;

    private List<QuotaDirectoryRestRep> quotaDirectories;

    public void precheck() {
        quotaDirectories = FileStorageUtils.getQuotaDirectories(ViPRExecutionTask.uris(quotaDirectoryIds));
    }

    public void deleteQuotaDirectories() {
        for (QuotaDirectoryRestRep qd : quotaDirectories) {
            URI qdId = qd.getId();
            FileStorageUtils.deactivateQuotaDirectory(qdId);
        }
    }
}

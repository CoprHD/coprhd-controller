/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.QuotaDirectoryRestRep;

public class GetQuotaDirectory extends ViPRExecutionTask<QuotaDirectoryRestRep> {
    private URI quotaDirectoryId;

    public GetQuotaDirectory(String quotaDirectoryId) {
        this(uri(quotaDirectoryId));
    }

    public GetQuotaDirectory(URI quotaDirectoryId) {
        this.quotaDirectoryId = quotaDirectoryId;
        provideDetailArgs(quotaDirectoryId);
    }

    @Override
    public QuotaDirectoryRestRep executeTask() throws Exception {
        return getClient().quotaDirectories().getQuotaDirectory(quotaDirectoryId);
    }
}
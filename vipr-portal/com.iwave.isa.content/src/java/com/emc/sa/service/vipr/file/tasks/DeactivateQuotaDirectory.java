/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.QuotaDirectoryDeleteParam;
import com.emc.storageos.model.file.QuotaDirectoryRestRep;
import com.emc.vipr.client.Task;

public class DeactivateQuotaDirectory extends WaitForTask<QuotaDirectoryRestRep> {
    private final URI quotaDirectoryId;

    public DeactivateQuotaDirectory(String quotaDirectoryId) {
        this(uri(quotaDirectoryId));
    }

    public DeactivateQuotaDirectory(URI quotaDirectoryId) {
        this.quotaDirectoryId = quotaDirectoryId;
        provideDetailArgs(quotaDirectoryId);
    }

    public URI getQuotaDirectoryId() {
        return quotaDirectoryId;
    }

    @Override
    protected Task<QuotaDirectoryRestRep> doExecute() throws Exception {
        QuotaDirectoryDeleteParam param = new QuotaDirectoryDeleteParam();
        param.setForceDelete(false);
        return getClient().quotaDirectories().deleteQuotaDirectory(quotaDirectoryId, param);
    }
}

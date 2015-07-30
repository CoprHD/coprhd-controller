/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.SmbShareResponse;

public class GetCifsSharesForFileSystem extends ViPRExecutionTask<List<SmbShareResponse>> {
    private URI fileSystemId;

    public GetCifsSharesForFileSystem(String fileSystemId) {
        this(uri(fileSystemId));
    }

    public GetCifsSharesForFileSystem(URI fileSystemId) {
        this.fileSystemId = fileSystemId;
        provideDetailArgs(fileSystemId);
    }

    @Override
    public List<SmbShareResponse> executeTask() throws Exception {
        return getClient().fileSystems().getShares(fileSystemId);
    }
}

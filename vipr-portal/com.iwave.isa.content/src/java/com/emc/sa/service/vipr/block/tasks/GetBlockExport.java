/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.export.ExportGroupRestRep;

public class GetBlockExport extends ViPRExecutionTask<ExportGroupRestRep> {
    private URI exportId;

    public GetBlockExport(String exportId) {
        this(uri(exportId));
    }

    public GetBlockExport(URI exportId) {
        this.exportId = exportId;
        provideDetailArgs(exportId);
    }

    @Override
    public ExportGroupRestRep executeTask() throws Exception {
        return getClient().blockExports().get(exportId);
    }
}

/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.vipr.client.Task;

public class DeactivateBlockExport extends WaitForTask<ExportGroupRestRep> {
    private URI exportId;

    public DeactivateBlockExport(String exportId) {
        this(uri(exportId));
    }

    public DeactivateBlockExport(URI exportId) {
        super();
        this.exportId = exportId;
        provideDetailArgs(exportId);
    }

    @Override
    protected Task<ExportGroupRestRep> doExecute() throws Exception {
        return getClient().blockExports().deactivate(exportId);
    }
}

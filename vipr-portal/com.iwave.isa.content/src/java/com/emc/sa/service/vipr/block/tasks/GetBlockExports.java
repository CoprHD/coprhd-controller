/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.export.ExportGroupRestRep;

public class GetBlockExports extends ViPRExecutionTask<List<ExportGroupRestRep>> {
    private List<URI> exportIds;

    public GetBlockExports(List<URI> exportIds) {
        this.exportIds = exportIds;
        setDetail("Export: %s", Arrays.toString(exportIds.toArray()));
    }

    @Override
    public List<ExportGroupRestRep> executeTask() throws Exception {
        return getClient().blockExports().getExports(exportIds);
    }
}

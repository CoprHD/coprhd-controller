/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.block.export.ITLRestRep;

/**
 */
public class FindBlockVolumeHlus extends ViPRExecutionTask<List<ITLRestRep>> {
    private Collection<URI> volumeIds;

    public FindBlockVolumeHlus(Collection<URI> volumeIds) {
        this.volumeIds = volumeIds;
        provideDetailArgs(volumeIds);
    }

    @Override
    public List<ITLRestRep> executeTask() throws Exception {
        BulkIdParam bulkParam = new BulkIdParam();
        for (URI id : volumeIds) {
            bulkParam.getIds().add(id);
        }
        return getClient().blockVolumes().getExports(bulkParam).getExportList();
    }
}

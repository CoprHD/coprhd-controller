/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.block.export.ITLRestRep;
import com.google.common.collect.Lists;

public class GetExportsForBlockObject extends ViPRExecutionTask<List<ITLRestRep>> {
    // id of block resource
    private URI blockResourceId;

    public GetExportsForBlockObject(String volumeId) {
        this(uri(volumeId));
    }

    public GetExportsForBlockObject(URI blockResourceId) {
        this.blockResourceId = blockResourceId;
        provideDetailArgs(blockResourceId); 
    }

    @Override
    public List<ITLRestRep> executeTask() throws Exception {
        if (ResourceType.isType(ResourceType.VOLUME, blockResourceId) || 
                ResourceType.isType(ResourceType.BLOCK_CONTINUOUS_COPY, blockResourceId)) {
            return getClient().blockVolumes().getExports(blockResourceId);
        }
        else if (ResourceType.isType(ResourceType.BLOCK_SNAPSHOT, blockResourceId)) {
            return getClient().blockSnapshots().listExports(blockResourceId);
        }
        else if (ResourceType.isType(ResourceType.VPLEX_CONTINUOUS_COPY, blockResourceId)) {
            return Lists.newArrayList();
        }
        throw new IllegalStateException("ID does not appear to be a volume or snapshot ["+blockResourceId+"]");
    }
}

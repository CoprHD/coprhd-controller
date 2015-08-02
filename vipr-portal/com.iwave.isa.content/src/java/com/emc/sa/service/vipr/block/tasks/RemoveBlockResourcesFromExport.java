/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportUpdateParam;
import com.emc.vipr.client.Task;
import com.google.common.collect.Sets;

public class RemoveBlockResourcesFromExport extends WaitForTask<ExportGroupRestRep> {

    private URI exportId;
    private Set<URI> resourceIds;

    public RemoveBlockResourcesFromExport(URI exportId, URI volumeId) {
        this(exportId, Collections.singleton(volumeId));
    }

    public RemoveBlockResourcesFromExport(URI exportId, Collection<URI> resourceIds) {
        super();
        this.exportId = exportId;
        this.resourceIds = Sets.newHashSet(resourceIds);
        provideDetailArgs(resourceIds, exportId);
    }

    @Override
    protected Task<ExportGroupRestRep> doExecute() throws Exception {
        ExportUpdateParam export = new ExportUpdateParam();
        for (URI volumeId : resourceIds) {
            export.removeVolume(volumeId);
        }
        return getClient().blockExports().update(exportId, export);
    }
}

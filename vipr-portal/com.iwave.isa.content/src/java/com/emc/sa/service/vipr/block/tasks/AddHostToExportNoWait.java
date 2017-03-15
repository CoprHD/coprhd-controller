/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportUpdateParam;
import com.emc.storageos.model.block.export.HostsUpdateParam;
import com.emc.vipr.client.Task;

public class AddHostToExportNoWait extends WaitForTask<ExportGroupRestRep> {

    private final URI exportId;
    private final URI hostId;

    public AddHostToExportNoWait(URI exportId, URI hostId) {
        super();
        this.exportId = exportId;
        this.hostId = hostId;
        provideDetailArgs(exportId, hostId);
    }

    @Override
    protected Task<ExportGroupRestRep> doExecute() throws Exception {
        ExportUpdateParam exportUpdateParam = new ExportUpdateParam();

        exportUpdateParam.setHosts(new HostsUpdateParam());
        exportUpdateParam.getHosts().getAdd().add(hostId);

        return getClient().blockExports().update(exportId, exportUpdateParam);
    }

}

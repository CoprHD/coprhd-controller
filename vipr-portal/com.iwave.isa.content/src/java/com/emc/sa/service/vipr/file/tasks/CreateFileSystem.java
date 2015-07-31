/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.sa.util.DiskSizeConversionUtils;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.vipr.client.Task;

public class CreateFileSystem extends WaitForTask<FileShareRestRep> {
    private final String label;
    private final double sizeInGB;
    private final URI vpoolId;
    private final URI varrayId;
    private final URI projectId;

    public CreateFileSystem(String label, double sizeInGB, String vpoolId, String varrayId, String projectId) {
        this(label, sizeInGB, uri(vpoolId), uri(varrayId), uri(projectId));
    }

    public CreateFileSystem(String label, double sizeInGB, URI vpoolId, URI varrayId, URI projectId) {
        this.label = label;
        this.sizeInGB = sizeInGB;
        this.vpoolId = vpoolId;
        this.varrayId = varrayId;
        this.projectId = projectId;
        provideDetailArgs(label, sizeInGB, vpoolId, varrayId, projectId);
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        FileSystemParam input = new FileSystemParam();
        input.setLabel(label);
        input.setSize(String.valueOf(DiskSizeConversionUtils.gbToBytes(sizeInGB)));
        input.setVpool(vpoolId);
        input.setVarray(varrayId);

        return getClient().fileSystems().create(projectId, input);
    }
}

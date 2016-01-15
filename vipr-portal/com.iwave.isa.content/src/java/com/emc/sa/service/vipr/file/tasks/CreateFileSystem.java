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
    private final int advisoryLimit;
    private final int softLimit;
    private final int gracePeriod;

    public CreateFileSystem(String label, double sizeInGB, int advisoryLimit, int softLimit, int gracePeriod, String vpoolId, String varrayId, String projectId) {
        this(label, sizeInGB, advisoryLimit, softLimit, gracePeriod,uri(vpoolId), uri(varrayId), uri(projectId));
    }
    
    public CreateFileSystem(String label, double sizeInGB, String vpoolId, String varrayId, String projectId) {
        this(label, sizeInGB, uri(vpoolId), uri(varrayId), uri(projectId));
    }
    
    public CreateFileSystem(String label, double sizeInGB, URI vpoolId, URI varrayId, URI projectId) {
        this(label, sizeInGB, 0, 0, 0,vpoolId, varrayId, projectId);
    }

    public CreateFileSystem(String label, double sizeInGB, int advisoryLimit, int softLimit, int gracePeriod, URI vpoolId, URI varrayId, URI projectId) {
        this.label = label;
        this.sizeInGB = sizeInGB;
        this.vpoolId = vpoolId;
        this.varrayId = varrayId;
        this.projectId = projectId;
        this.advisoryLimit= advisoryLimit;
        this.softLimit = softLimit;
        this.gracePeriod = gracePeriod;
        provideDetailArgs(label, sizeInGB, advisoryLimit, softLimit, gracePeriod ,vpoolId, varrayId, projectId);
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        FileSystemParam input = new FileSystemParam();
        input.setLabel(label);
        input.setSize(String.valueOf(DiskSizeConversionUtils.gbToBytes(sizeInGB)));
        input.setVpool(vpoolId);
        input.setVarray(varrayId);
        input.setNotificationLimit(advisoryLimit);
        input.setSoftLimit(softLimit);
        input.setSoftGrace(gracePeriod);

        return getClient().fileSystems().create(projectId, input);
    }
}

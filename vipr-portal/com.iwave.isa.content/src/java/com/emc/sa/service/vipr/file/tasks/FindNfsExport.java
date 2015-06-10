/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.FileSystemExportParam;

public class FindNfsExport extends ViPRExecutionTask<FileSystemExportParam> {

    private final URI fileSystemId;
    private final String nfsExportMountPoint;
    private final List<FileSystemExportParam> nfsExports;

    public FindNfsExport(String fileSystemId, String nfsExportMountPoint, List<FileSystemExportParam> nfsExports) {
        this(uri(fileSystemId), nfsExportMountPoint, nfsExports);
    }

    public FindNfsExport(URI fileSystemId, String nfsExportMountPoint, List<FileSystemExportParam> nfsExports) {
        this.fileSystemId = fileSystemId;
        this.nfsExportMountPoint = nfsExportMountPoint;
        this.nfsExports = nfsExports;
        provideDetailArgs(fileSystemId, nfsExportMountPoint);
    }

    @Override
    public FileSystemExportParam executeTask() throws Exception {
        for (FileSystemExportParam export : nfsExports) {
            if (export.getMountPoint().equals(nfsExportMountPoint)) {
                return export;
            }
        }
        throw stateException("illegalState.findNfsExport", nfsExportMountPoint, fileSystemId);
    }

}

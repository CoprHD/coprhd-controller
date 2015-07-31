/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Task;

public class DeactivateFileSystemExport extends WaitForTask<FileShareRestRep> {
    private final URI fileSystemId;
    private final String protocol;
    private final String sectype;
    private final String perm;
    private final String rootMapping;

    public DeactivateFileSystemExport(String fileSystemId, String protocol, String sectype, String perm,
            String rootMapping) {
        this(uri(fileSystemId), protocol, sectype, perm, rootMapping);
    }

    public DeactivateFileSystemExport(URI fileSystemId, String protocol, String sectype, String perm, String rootMapping) {
        this.fileSystemId = fileSystemId;
        this.protocol = protocol;
        this.sectype = sectype;
        this.perm = perm;
        this.rootMapping = rootMapping;
        provideDetailArgs(fileSystemId, protocol, sectype, perm, rootMapping);
    }

    public URI getFileSystemId() {
        return fileSystemId;
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        return getClient().fileSystems().removeExport(fileSystemId, protocol, sectype, perm, rootMapping);
    }
}

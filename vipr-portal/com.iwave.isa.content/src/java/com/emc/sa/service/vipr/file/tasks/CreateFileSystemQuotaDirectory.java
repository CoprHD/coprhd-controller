/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.sa.util.DiskSizeConversionUtils;
import com.emc.storageos.model.file.QuotaDirectoryCreateParam;
import com.emc.storageos.model.file.QuotaDirectoryRestRep;
import com.emc.vipr.client.Task;

public class CreateFileSystemQuotaDirectory extends WaitForTask<QuotaDirectoryRestRep> {
    private URI fileSystemId;
    private String name;
    private Boolean oplock;
    private String securityStyle;
    private String size;

    public CreateFileSystemQuotaDirectory(String fileSystemId, String name, Boolean oplock, String securityStyle, String size) {
        this(uri(fileSystemId), name, oplock, securityStyle, size);
    }

    public CreateFileSystemQuotaDirectory(URI fileSystemId, String name, Boolean oplock, String securityStyle, String size) {
        this.fileSystemId = fileSystemId;
        this.name = name;
        this.oplock = oplock;
        this.securityStyle = securityStyle;
        this.size = size;

        provideDetailArgs(fileSystemId, name, oplock, securityStyle, size);
    }

    @Override
    protected Task<QuotaDirectoryRestRep> doExecute() throws Exception {
        QuotaDirectoryCreateParam quotaDir = new QuotaDirectoryCreateParam();
        quotaDir.setQuotaDirName(name);
        quotaDir.setOpLock(oplock);
        quotaDir.setSecurityStyle(securityStyle);
        if (size == null || size.isEmpty()) {
            size = "0";
        }
        quotaDir.setSize(String.valueOf(DiskSizeConversionUtils.gbToBytes(new Long(size))));

        return getClient().quotaDirectories().createQuotaDirectory(fileSystemId, quotaDir);
    }
}

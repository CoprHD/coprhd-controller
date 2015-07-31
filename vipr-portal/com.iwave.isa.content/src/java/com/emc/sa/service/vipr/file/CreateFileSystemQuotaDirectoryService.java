/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("CreateFileSystemQuotaDirectory")
public class CreateFileSystemQuotaDirectoryService extends ViPRService {
    @Bindable
    protected CreateFileSystemQuotaDirectoryHelper createFileSystemQuotaDirectoryHelper =
            new CreateFileSystemQuotaDirectoryHelper();

    @Override
    public void precheck() {
        createFileSystemQuotaDirectoryHelper.precheck();
    }

    @Override
    public void execute() {
        createFileSystemQuotaDirectoryHelper.createFileSystemQuotaDirectories();
    }
}

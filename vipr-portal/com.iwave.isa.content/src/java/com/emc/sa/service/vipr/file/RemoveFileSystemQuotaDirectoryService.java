/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("RemoveFileSystemQuotaDirectory")
public class RemoveFileSystemQuotaDirectoryService extends ViPRService {
    @Bindable
    protected RemoveFileSystemQuotaDirectoryHelper removeFileSystemQuotaDirectoryHelper =
            new RemoveFileSystemQuotaDirectoryHelper();

    @Override
    public void precheck() {
        removeFileSystemQuotaDirectoryHelper.precheck();
    }

    @Override
    public void execute() {
        removeFileSystemQuotaDirectoryHelper.deleteQuotaDirectories();
    }
}

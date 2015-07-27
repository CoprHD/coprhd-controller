/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("NasRemoveFileSystem")
public class DeleteFileSystemService extends ViPRService {
    @Bindable
    protected DeleteFileSystemHelper deleteFileSystemHelper = new DeleteFileSystemHelper();

    @Override
    public void precheck() {
        deleteFileSystemHelper.precheck();
    }

    @Override
    public void execute() {
        deleteFileSystemHelper.deleteFileSystems();
    }
}

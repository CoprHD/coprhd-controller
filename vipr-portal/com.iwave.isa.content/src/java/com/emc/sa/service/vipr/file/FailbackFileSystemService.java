/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.FAILBACK_TARGET_FILE;
import static com.emc.sa.service.ServiceParams.FILESYSTEM;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("FailbackFileSystem")
public class FailbackFileSystemService extends ViPRService {

    @Param(FILESYSTEM)
    protected URI failbackSource;

    @Param(FAILBACK_TARGET_FILE)
    protected URI failbackTarget;

    @Override
    public void execute() throws Exception {
        FileStorageUtils.failbackFileSystem(failbackSource, failbackTarget);
    }
}
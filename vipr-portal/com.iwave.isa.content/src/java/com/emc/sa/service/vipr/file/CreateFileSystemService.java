/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.VOLUME_NAME;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("CreateFileSystem")
public class CreateFileSystemService extends ViPRService {
    @Param(VIRTUAL_POOL)
    protected URI virtualPool;

    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;

    @Param(PROJECT)
    protected URI project;

    @Param(SIZE_IN_GB)
    protected Double sizeInGb;

    @Param(VOLUME_NAME)
    protected String shareName;
    
    @Override
    public void execute() throws Exception {
        FileStorageUtils.createFileSystem(project, virtualArray, virtualPool, shareName, sizeInGb);
    }
}
/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.FILESYSTEMS;
import static com.emc.sa.service.ServiceParams.FILESYSTEM_TRGT_VARRAY;
import static com.emc.sa.service.ServiceParams.FILE_POLICY;
import static com.emc.sa.service.ServiceParams.TARGET_VIRTUAL_POOL;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("ChangeFileVirtualPool")
public class ChangeFileVirtualPoolService extends ViPRService {

    @Param(TARGET_VIRTUAL_POOL)
    protected URI targetVirtualPool;

    @Param(FILESYSTEMS)
    protected String fileId;

    @Param(FILE_POLICY)
    protected URI filePolicy;

    @Param(FILESYSTEM_TRGT_VARRAY)
    protected URI targetVirtualArray;

    @Override
    public void execute() throws Exception {
        FileStorageUtils.changeFileVirtualPool(uri(fileId), targetVirtualPool, filePolicy, targetVirtualArray);
    }
}

/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.FILESYSTEMS;
import static com.emc.sa.service.ServiceParams.FILESYSTEM_SRC_VARRAY;
import static com.emc.sa.service.ServiceParams.FILESYSTEM_TRGT_VARRAY;
import static com.emc.sa.service.ServiceParams.FILE_POLICY;
import static com.emc.sa.service.ServiceParams.PROJECT;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("AssociateFilePolicyToFileSystem")
public class AssociateFilePolicyToFileSystemService extends ViPRService {

    @Param(PROJECT)
    protected URI project;

    @Param(FILESYSTEMS)
    protected URI fileSystem;

    @Param(FILESYSTEM_SRC_VARRAY)
    protected URI sourceVirtualArray;

    @Param(FILE_POLICY)
    protected URI filePolicy;

    @Param(FILESYSTEM_TRGT_VARRAY)
    protected URI targetVirtualArray;

    @Override
    public void execute() throws Exception {
        FileStorageUtils.associateFilePolicy(fileSystem, filePolicy, targetVirtualArray);
    }

}

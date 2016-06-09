/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import static com.emc.sa.service.ServiceParams.FILESYSTEM;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.linux.LinuxService;
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.storageos.model.file.FileSystemExportParam;

@Service("Linux-MountNFSExport")
public class MountNFSExportService extends LinuxService {

    @Param(FILESYSTEM)
    protected URI fsId;

    private FileSystemExportParam export;

    protected MountNFSExportHelper mountNFSExportHelper;

    @Override
    public void init() throws Exception {
        super.init();
        mountNFSExportHelper = MountNFSExportHelper.createHelper(linuxSystem, hostPorts);
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        export = FileStorageUtils.getNfsExports(fsId).get(0);
        acquireHostsLock();
        mountNFSExportHelper.precheck();
    }

    @Override
    public void execute() throws Exception {
        export = FileStorageUtils.getNfsExports(fsId).get(0);
        mountNFSExportHelper.mountExport(export);
    }
}

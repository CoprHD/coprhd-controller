/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import static com.emc.sa.service.ServiceParams.FILESYSTEM;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.storageos.model.file.FileShareRestRep;

@Service("LinuxMountNFSExport")
public class MountNFSExportService extends LinuxFileService {

    @Param(FILESYSTEM)
    protected URI fsId;

    private FileShareRestRep fs;

    protected MountNFSExportHelper mountNFSExportHelper;

    @Override
    public void init() throws Exception {
        super.init();
        mountNFSExportHelper = MountNFSExportHelper.createHelper(linuxSystem);
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        fs = FileStorageUtils.getFileSystem(fsId);
        acquireHostsLock();
        mountNFSExportHelper.precheck();
    }

    @Override
    public void execute() throws Exception {
        mountNFSExportHelper.mountExport(fs, hostId);
        ExecutionUtils.addAffectedResource(hostId.toString());
    }
}

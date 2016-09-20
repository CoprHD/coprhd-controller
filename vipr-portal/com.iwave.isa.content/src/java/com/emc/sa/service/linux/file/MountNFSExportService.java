/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import static com.emc.sa.service.ServiceParams.FILESYSTEM;
import static com.emc.sa.service.ServiceParams.FS_TYPE;
import static com.emc.sa.service.ServiceParams.MOUNT_PATH;
import static com.emc.sa.service.ServiceParams.SECURITY_TYPE;
import static com.emc.sa.service.ServiceParams.SUBDIRECTORY;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.linux.LinuxService;
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.storageos.model.file.FileShareRestRep;

@Service("LinuxMountNFSExport")
public class MountNFSExportService extends LinuxService {

    @Param(FILESYSTEM)
    protected URI fsId;

    @Param(MOUNT_PATH)
    protected String mountPath;

    @Param(SUBDIRECTORY)
    protected String subDirectory;

    @Param(SECURITY_TYPE)
    protected String securityType;

    @Param(FS_TYPE)
    protected String fsType;

    private FileShareRestRep fs;

    protected MountNFSExportHelper mountNFSExportHelper;

    @Override
    public void init() throws Exception {
        super.init();
        mountNFSExportHelper = MountNFSExportHelper.createHelper();
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        fs = FileStorageUtils.getFileSystem(fsId);
    }

    @Override
    public void execute() throws Exception {
        String subDir = subDirectory;
        if ("!No subdirectory".equalsIgnoreCase(subDir)) {
            subDir = null;
        }
        mountNFSExportHelper.mountExport(fsId, hostId, subDir, mountPath, securityType, host.getHostName(), fsType);
        ExecutionUtils.addAffectedResource(hostId.toString());
    }
}

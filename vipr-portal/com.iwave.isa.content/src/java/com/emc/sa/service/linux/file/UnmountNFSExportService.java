/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import static com.emc.sa.service.ServiceParams.MOUNTED_NFS_EXPORTS;

import java.util.ArrayList;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.sa.service.linux.LinuxService;
import com.emc.storageos.model.file.MountInfo;

@Service("LinuxUnmountNFSExport")
public class UnmountNFSExportService extends LinuxService {

    @Param(MOUNTED_NFS_EXPORTS)
    protected List<String> mountTags;

    private List<MountInfo> mountList;

    private UnmountNFSExportHelper unmountNFSExportHelper;

    @Override
    public void init() throws Exception {
        super.init();
        unmountNFSExportHelper = UnmountNFSExportHelper.createHelper(hostId);
        mountList = new ArrayList<MountInfo>();
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
    }

    @Override
    public void execute() {
        mountList = MachineTagUtils.convertMountStringToMounts(mountTags);
        unmountNFSExportHelper.setMounts(mountList);
        unmountNFSExportHelper.unmountExports();
        if (hostId != null) {
            ExecutionUtils.addAffectedResource(hostId.toString());
        }
    }
}

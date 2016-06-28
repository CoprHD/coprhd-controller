/*
 * Copyright (c) 2012-2015 iWave Software LLC
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

@Service("LinuxUnmountNFSExport")
public class UnmountNFSExportService extends LinuxFileService {

    @Param(MOUNTED_NFS_EXPORTS)
    protected List<String> mountTags;

    private List<MountInfo> mountList;

    private UnmountNFSExportHelper unmountNFSExportHelper;

    @Override
    public void init() throws Exception {
        super.init();
        unmountNFSExportHelper = UnmountNFSExportHelper.createHelper(linuxSystem);
        mountList = new ArrayList<MountInfo>();
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        mountList = MachineTagUtils.convertNFSTagsToMounts(mountTags);
        acquireHostsLock();
        unmountNFSExportHelper.setMounts(mountList);
    }

    @Override
    public void execute() {
        unmountNFSExportHelper.unmountExports();
        if (hostId != null) {
            ExecutionUtils.addAffectedResource(hostId.toString());
        }
    }
}

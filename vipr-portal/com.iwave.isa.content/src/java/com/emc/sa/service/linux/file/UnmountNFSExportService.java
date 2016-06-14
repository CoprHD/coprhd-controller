/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import static com.emc.sa.service.ServiceParams.FILESYSTEMS;

import java.util.ArrayList;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.linux.LinuxService;
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;

@Service("Linux-UnmountBlockVolume")
public class UnmountNFSExportService extends LinuxService {

    @Param(FILESYSTEMS)
    protected List<String> fsIds;

    private List<FileSystemExportParam> exportList;

    private UnmountNFSExportHelper unmountNFSExportHelper;

    @Override
    public void init() throws Exception {
        super.init();
        unmountNFSExportHelper = UnmountNFSExportHelper.createHelper(linuxSystem, hostPorts);
        exportList = new ArrayList<FileSystemExportParam>();
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        List<FileShareRestRep> fsList = FileStorageUtils.getFileSystems(uris(fsIds));
        for (FileShareRestRep fs : fsList) {
            exportList.addAll(FileStorageUtils.getNfsExports(fsList.get(0).getId()));
        }
        acquireHostsLock();
        unmountNFSExportHelper.setExports(exportList);
        unmountNFSExportHelper.precheck();
    }

    @Override
    public void execute() {
        unmountNFSExportHelper.unmountVolumes();
        if (hostId != null) {
            ExecutionUtils.addAffectedResource(hostId.toString());
        }
    }
}

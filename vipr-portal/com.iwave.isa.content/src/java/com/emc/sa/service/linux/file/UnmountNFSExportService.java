/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import static com.emc.sa.service.ServiceParams.MOUNTED_NFS_EXPORTS;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;

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

    public void convertTagsToMounts() {
        for (String tag : mountTags) {
            String[] pieces = StringUtils.trim(tag).split("\\s+");
            MountInfo mountInfo = new MountInfo();
            if (pieces.length > 1) {
                mountInfo.setHostId(uri(pieces[1]));
            }
            if (pieces.length > 2) {
                mountInfo.setMountPoint(pieces[2]);
            }
            if (pieces.length > 3) {
                mountInfo.setSubDirectory(pieces[3]);
            }
            if (pieces.length > 4) {
                mountInfo.setSecurityType(pieces[4]);
            }
            if (pieces.length > 5) {
                mountInfo.setFsId(uri(pieces[5]));
            }
            mountInfo.setTag(tag);
            mountList.add(mountInfo);
        }
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        convertTagsToMounts();
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

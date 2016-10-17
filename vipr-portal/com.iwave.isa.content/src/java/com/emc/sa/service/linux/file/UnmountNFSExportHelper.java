/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.file.MountInfo;
import com.google.common.collect.Lists;

public class UnmountNFSExportHelper {

    private List<MountInfo> mountList;

    private String hostname;

    public static UnmountNFSExportHelper createHelper(URI hostId) {
        UnmountNFSExportHelper unmountNFSExportHelper = new UnmountNFSExportHelper(hostId);
        BindingUtils.bind(unmountNFSExportHelper, ExecutionUtils.currentContext().getParameters());
        return unmountNFSExportHelper;
    }

    private UnmountNFSExportHelper(URI hostId) {
        Host host = BlockStorageUtils.getHost(hostId);
        this.hostname = host.getHostName();
    }

    public void setMounts(List<MountInfo> mountList) {
        this.mountList = Lists.newArrayList();
        this.mountList.addAll(mountList);
    }

    public void unmountExports() {
        for (MountInfo mount : mountList) {
            logInfo("linux.mount.file.export.unmount", mount.getMountPath(), hostname);
            FileStorageUtils.unmountNFSExport(mount.getFsId(), mount.getHostId(), mount.getMountPath());
        }
    }
}

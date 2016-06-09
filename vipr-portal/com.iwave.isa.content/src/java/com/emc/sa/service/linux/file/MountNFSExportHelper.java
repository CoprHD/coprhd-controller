/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import static com.emc.sa.service.ServiceParams.MOUNT_POINT;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.linux.LinuxSupport;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.iwave.ext.linux.LinuxSystemCLI;

public class MountNFSExportHelper {

    private static final String AUTO = "auto";

    private final LinuxSupport linuxSupport;
    private final String hostname;

    @Param(MOUNT_POINT)
    protected String mountPoint;

    public static MountNFSExportHelper createHelper(LinuxSystemCLI linuxSystem, List<Initiator> hostPorts) {
        LinuxSupport linuxSupport = new LinuxSupport(linuxSystem, hostPorts);
        MountNFSExportHelper mountNFSExportHelper = new MountNFSExportHelper(linuxSupport);
        BindingUtils.bind(mountNFSExportHelper, ExecutionUtils.currentContext().getParameters());
        return mountNFSExportHelper;
    }

    private MountNFSExportHelper(LinuxSupport linuxSupport) {
        this.linuxSupport = linuxSupport;
        this.hostname = linuxSupport.getHostName();
    }

    public void precheck() {
        linuxSupport.verifyMountPoint(mountPoint);
    }

    public void mountExport(FileSystemExportParam export) {
        logInfo("linux.mount.block.volume.mount", hostname, export.getMountPoint(), mountPoint, AUTO, export.getSubDirectory());
        linuxSupport.createDirectory(mountPoint);
        linuxSupport.addToFSTab(export.getMountPoint(), mountPoint, AUTO, null);
        linuxSupport.mountPath(mountPoint);
    }
}

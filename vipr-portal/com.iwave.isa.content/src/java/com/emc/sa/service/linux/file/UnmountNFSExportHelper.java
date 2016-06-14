/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.service.linux.LinuxSupport;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.iwave.ext.linux.LinuxSystemCLI;

public class UnmountNFSExportHelper {
    private final LinuxSupport linux;
    private List<FileSystemExportParam> mountedExports;

    public static UnmountNFSExportHelper createHelper(LinuxSystemCLI linuxSystem, List<Initiator> hostPorts) {
        LinuxSupport linuxSupport = new LinuxSupport(linuxSystem, hostPorts);
        UnmountNFSExportHelper unmountNFSExportHelper = new UnmountNFSExportHelper(linuxSupport);
        BindingUtils.bind(unmountNFSExportHelper, ExecutionUtils.currentContext().getParameters());
        return unmountNFSExportHelper;
    }

    private UnmountNFSExportHelper(LinuxSupport linuxSupport) {
        this.linux = linuxSupport;
    }

    public void setExports(List<FileSystemExportParam> exports) {
        this.mountedExports = Lists.newArrayList();
        mountedExports.addAll(exports);
    }

    public void precheck() {
        linux.findMountPoints(mountedExports);
        linux.ensureVolumesAreMounted(mountedExports);
    }

    public void unmountVolumes() {
        Set<URI> untaggedVolumeIds = Sets.newHashSet();
        for (FileSystemExportParam export : mountedExports) {

            // unmount the volume
            linux.unmountPath(export.getMountPoint());

            // remove from fstab
            linux.removeFromFSTab(export.getMountPoint());

            // delete the directory entry if it's empty
            if (linux.isDirectoryEmpty(export.getMountPoint())) {
                linux.deleteDirectory(export.getMountPoint());
            }
        }
    }
}

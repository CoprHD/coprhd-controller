/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import static com.emc.sa.service.ServiceParams.DESTINATION_PATH;
import static com.emc.sa.service.ServiceParams.SECURITY_TYPE;
import static com.emc.sa.service.ServiceParams.SUBDIRECTORY;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.iwave.ext.linux.LinuxSystemCLI;

public class MountNFSExportHelper {

    private static final String AUTO = "auto";

    @Param(DESTINATION_PATH)
    protected String destinationPath;

    @Param(SUBDIRECTORY)
    protected String subDirectory;

    @Param(SECURITY_TYPE)
    protected String securityType;

    private final LinuxFileSupport linuxSupport;
    private final String hostname;

    public static MountNFSExportHelper createHelper(LinuxSystemCLI linuxSystem) {
        LinuxFileSupport linuxSupport = new LinuxFileSupport(linuxSystem);
        MountNFSExportHelper mountNFSExportHelper = new MountNFSExportHelper(linuxSupport);
        BindingUtils.bind(mountNFSExportHelper, ExecutionUtils.currentContext().getParameters());
        return mountNFSExportHelper;
    }

    private MountNFSExportHelper(LinuxFileSupport linuxSupport) {
        this.linuxSupport = linuxSupport;
        this.hostname = linuxSupport.getHostName();
    }

    public void precheck() {
        logInfo("linux.mount.file.export.verify", hostname);
        linuxSupport.verifyMountPoint(destinationPath);
    }

    public FileSystemExportParam findExport(FileShareRestRep fs, String subDirectory, String securityType) {
        List<FileSystemExportParam> exportList = FileStorageUtils.getNfsExports(fs.getId());
        if (subDirectory.equalsIgnoreCase("!nodir")) {
            for (FileSystemExportParam export : exportList) {
                if (export.getSubDirectory().isEmpty() && securityType.equals(export.getSecurityType())) {
                    return export;
                }
            }
        }
        for (FileSystemExportParam export : exportList) {
            if (subDirectory.equals(export.getSubDirectory()) && securityType.equals(export.getSecurityType())) {
                return export;
            }
        }
        throw new IllegalArgumentException("no exports found");
    }

    public void mountExport(FileShareRestRep fs, URI hostId) {
        FileSystemExportParam export = findExport(fs, subDirectory, securityType);
        logInfo("linux.mount.file.export.mount", export.getMountPoint(), destinationPath, hostname);
        // Create directory
        linuxSupport.createDirectory(destinationPath);
        // Add to the /etc/fstab to allow the os to mount on restart
        linuxSupport.addToFSTab(export.getMountPoint(), destinationPath, AUTO, "nolock,sec=" + securityType);
        // Mount the device
        linuxSupport.mountPath(destinationPath);
        // Set the fs tag containing mount info
        linuxSupport.setFSTag(fs.getId().toString(), MachineTagUtils.generateMountTag(hostId, destinationPath, subDirectory, securityType));
        ExecutionUtils.addAffectedResource(fs.getId().toString());
    }
}

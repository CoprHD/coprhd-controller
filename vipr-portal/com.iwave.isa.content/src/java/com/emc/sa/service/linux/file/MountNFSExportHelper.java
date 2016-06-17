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
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.iwave.ext.linux.LinuxSystemCLI;

public class MountNFSExportHelper {

    private static final String AUTO = "auto";

    private final LinuxFileSupport linuxSupport;
    private final String hostname;

    @Param(DESTINATION_PATH)
    protected String destinationPath;

    @Param(SUBDIRECTORY)
    protected String subDirectory;

    @Param(SECURITY_TYPE)
    protected String securityType;

    private FileSystemExportParam export;

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
        linuxSupport.verifyMountPoint(destinationPath);
    }

    public String generateMountTag(URI fsId, URI hostId) {
        return "mountNfs " + hostId.toString() + " " + fsId.toString() + " " + destinationPath + " " + subDirectory + " " + securityType;
    }

    public FileSystemExportParam findExport(FileShareRestRep fs) {
        List<FileSystemExportParam> exportList = FileStorageUtils.getNfsExports(fs.getId());
        for (FileSystemExportParam export : exportList) {
            if (subDirectory.equals(export.getSubDirectory()) && securityType.equals(export.getSecurityType())) {
                return export;
            }
        }
        throw new IllegalArgumentException("no exports found");
    }

    public void mountExport(FileShareRestRep fs) {
        logInfo("linux.mount.file.export.mount", hostname, findExport(fs).getMountPoint(), destinationPath, AUTO, subDirectory);
        linuxSupport.createDirectory(destinationPath);
        linuxSupport.addToFSTab(fs.getMountPath(), destinationPath, AUTO, null);
        linuxSupport.mountPath(destinationPath, securityType);
        ExecutionUtils.addAffectedResource(fs.getId().toString());
    }
}

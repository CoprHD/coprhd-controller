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
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;

/**
 * 
 * @author yelkaa
 * 
 */

public class MountNFSExportHelper {

    public static MountNFSExportHelper createHelper() {
        MountNFSExportHelper mountNFSExportHelper = new MountNFSExportHelper();
        BindingUtils.bind(mountNFSExportHelper, ExecutionUtils.currentContext().getParameters());
        return mountNFSExportHelper;
    }

    private MountNFSExportHelper() {

    }

    public FileSystemExportParam findExport(FileShareRestRep fs, String subDirectory, String securityType) {
        List<FileSystemExportParam> exportList = FileStorageUtils.getNfsExports(fs.getId());
        if (subDirectory == null || subDirectory.equalsIgnoreCase("!nodir")) {
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

    public void mountExport(URI fsId, URI hostId, String subDirectory, String mountPath, String securityType, String hostName,
            String fsType) {
        FileShareRestRep fs = FileStorageUtils.getFileSystem(fsId);
        FileSystemExportParam export = findExport(fs, subDirectory, securityType);
        logInfo("linux.mount.file.export.mount", export.getMountPoint(), mountPath, hostName);
        FileStorageUtils.mountNfsExport(hostId, fs.getId(), subDirectory, mountPath, securityType, fsType);
        ExecutionUtils.addAffectedResource(fs.getId().toString());
    }
}

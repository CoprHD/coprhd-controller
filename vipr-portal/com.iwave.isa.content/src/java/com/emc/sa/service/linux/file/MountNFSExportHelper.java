/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import static com.emc.sa.service.ServiceParams.MOUNT_PATH;
import static com.emc.sa.service.ServiceParams.SECURITY_TYPE;
import static com.emc.sa.service.ServiceParams.SUBDIRECTORY;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;

/**
 * 
 * @author yelkaa
 *
 */

public class MountNFSExportHelper {

    @Param(MOUNT_PATH)
    protected String mountPath;

    @Param(SUBDIRECTORY)
    protected String subDirectory;

    @Param(SECURITY_TYPE)
    protected String securityType;

    private final String hostname;

    public static MountNFSExportHelper createHelper(URI hostId) {
        MountNFSExportHelper mountNFSExportHelper = new MountNFSExportHelper(hostId);
        BindingUtils.bind(mountNFSExportHelper, ExecutionUtils.currentContext().getParameters());
        return mountNFSExportHelper;
    }

    private MountNFSExportHelper(URI hostId) {
        Host host = BlockStorageUtils.getHost(hostId);
        this.hostname = host.getHostName();
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
        logInfo("linux.mount.file.export.mount", export.getMountPoint(), mountPath, hostname);
        FileStorageUtils.mountNfsExport(hostId, fs.getId(), subDirectory, mountPath, securityType, "auto");
        ExecutionUtils.addAffectedResource(fs.getId().toString());
    }
}

/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import static com.emc.sa.service.ServiceParams.COMMENT;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.VOLUME_NAME;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;

/**
 * 
 * @author yelkaa
 *
 */

public class CreateNFSExportAndMountHelper {

    @Param(VIRTUAL_POOL)
    protected URI virtualPool;
    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;
    @Param(PROJECT)
    protected URI project;
    @Param(COMMENT)
    protected String comment;
    @Param(SIZE_IN_GB)
    protected Double sizeInGb;
    @Param(VOLUME_NAME)
    protected String exportName;

    public static CreateNFSExportAndMountHelper createHelper() {
        CreateNFSExportAndMountHelper createNFSExportAndMountHelper = new CreateNFSExportAndMountHelper();
        BindingUtils.bind(createNFSExportAndMountHelper, ExecutionUtils.currentContext().getParameters());
        return createNFSExportAndMountHelper;
    }

    private CreateNFSExportAndMountHelper() {

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

    public void mountExport(FileShareRestRep fs, URI hostId, String subDirectory, String mountPath, String securityType, String hostName) {
        FileSystemExportParam export = findExport(fs, subDirectory, securityType);
        logInfo("linux.mount.file.export.mount", export.getMountPoint(), mountPath, hostName);
        FileStorageUtils.mountNfsExport(hostId, fs.getId(), subDirectory, mountPath, securityType, "auto");
        ExecutionUtils.addAffectedResource(fs.getId().toString());
    }
}

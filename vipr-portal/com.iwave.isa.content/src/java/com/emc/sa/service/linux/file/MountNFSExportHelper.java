/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.storageos.model.file.FileShareRestRep;

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

    public void mountExport(URI fsId, URI hostId, String subDirectory, String mountPath, String securityType, String hostName,
            String fsType) {
        FileShareRestRep fs = FileStorageUtils.getFileSystem(fsId);
        FileStorageUtils.mountNFSExport(hostId, fs.getId(), subDirectory, mountPath, securityType, fsType);
        ExecutionUtils.addAffectedResource(fs.getId().toString());
    }
}

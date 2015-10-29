/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.file;

import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.storageos.volumecontroller.FileControllerConstants;

@Service("VMware-DeleteNfsDatastoreAndExport")
public class DeleteNfsDatastoreAndExportService extends DeleteNfsDatastoreService {
    @Override
    public void execute() throws Exception {
        super.execute();
        FileStorageUtils.deleteFileSystem(fileSystem.getId(), FileControllerConstants.DeleteTypeEnum.FULL);
    }
}

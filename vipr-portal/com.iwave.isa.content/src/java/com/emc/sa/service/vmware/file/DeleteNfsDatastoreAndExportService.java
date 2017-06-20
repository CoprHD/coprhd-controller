/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.file;

import com.emc.sa.engine.service.Service;

@Service("VMware-DeleteNfsDatastoreAndExport")
public class DeleteNfsDatastoreAndExportService extends DeleteNfsDatastoreService {
    @Override
    public void execute() throws Exception {
        StringBuffer errorMsg = new StringBuffer();
        errorMsg.append("This operation is currently not supported. Please follow the below steps to delete Datastore:\r\n")
                .append("1. Remove all Virtual Machines and data from Datastore using VMware vCenter\r\n")
                .append("2. Use ViPR Controller Catalog Services in the following order\r\n")
                .append("   a. \"File Services for VMware vCenter->Delete VMware NFS Datastore\", \r\n")
                .append("   b. \"File Storage Services ->Remove NFS Export for File System\" and \r\n")
                .append("   c. \"File Storage Services ->Remove File System\"");
        Exception deleteDataStoreNotSupported = new Exception(errorMsg.toString());
        throw deleteDataStoreNotSupported;
        // For this patch commenting this code
        // COP-31252
        // super.execute();
        // FileStorageUtils.deleteFileSystem(fileSystem.getId(), FileControllerConstants.DeleteTypeEnum.FULL);
    }
}

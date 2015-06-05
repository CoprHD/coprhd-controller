/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SHARE_COMMENT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.VOLUME_NAME;
import java.net.URI;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.storageos.model.file.FileShareRestRep;

public class CreateCifsShareHelper {
    
    @Param(VIRTUAL_POOL)
    protected URI virtualPool;

    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;

    @Param(PROJECT)
    protected URI project;

    @Param(SIZE_IN_GB)
    protected Double sizeInGb;

    @Param(VOLUME_NAME)
    protected String shareName;

    @Param(SHARE_COMMENT)
    protected String shareComment;
    
    @Bindable(itemType = FileStorageUtils.FileSystemACLs.class)
    protected FileStorageUtils.FileSystemACLs[] fileSystemShareACLs;
    
    protected URI fileSystemId;

    public FileShareRestRep createCifsShare() {
        this.fileSystemId = FileStorageUtils.createFileSystem(project, virtualArray, virtualPool, shareName, sizeInGb);
        FileStorageUtils.createCifsShare(this.fileSystemId, shareName, shareComment, null);
        return FileStorageUtils.getFileSystem(this.fileSystemId);
    }
    
    public void setFileSystemShareACL() {
        if (fileSystemShareACLs != null && fileSystemShareACLs.length > 0) {
            FileStorageUtils.setFileSystemShareACL(this.fileSystemId, shareName, fileSystemShareACLs);
        }
    }
}

/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.ADVISORY_LIMIT;
import static com.emc.sa.service.ServiceParams.GRACE_PERIOD;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SHARE_COMMENT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.SOFT_LIMIT;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.VOLUME_NAME;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
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

    @Param(value = SOFT_LIMIT, required = false)
    protected Double softLimit;

    @Param(value = ADVISORY_LIMIT, required = false)
    protected Double advisoryLimit;

    @Param(value = GRACE_PERIOD, required = false)
    protected Double gracePeriod;

    @Bindable(itemType = FileStorageUtils.FileSystemACLs.class)
    protected FileStorageUtils.FileSystemACLs[] fileSystemShareACLs;

    protected URI fileSystemId;

    public void precheckFileACLs() {
        if (fileSystemShareACLs != null && fileSystemShareACLs.length > 0) {
            List<String> invalidNames = FileStorageUtils.getInvalidFileACLs(fileSystemShareACLs);
            if (!invalidNames.isEmpty()) {
                ExecutionUtils.fail("failTask.CreateCifsShareHelper.invalidName", invalidNames, invalidNames);
            }
            fileSystemShareACLs = FileStorageUtils.clearEmptyFileACLs(fileSystemShareACLs);
        }
    }

    public FileShareRestRep createCifsShare() {
        int tempSoftLimit = (softLimit != null) ? softLimit.intValue() : 0;
        int tempAdvisoryLimit = (advisoryLimit != null) ? advisoryLimit.intValue() : 0;
        int tempGracePeriod = (gracePeriod != null) ? gracePeriod.intValue() : 0;

        this.fileSystemId = FileStorageUtils.createFileSystem(project, virtualArray, virtualPool, shareName, sizeInGb, tempAdvisoryLimit,
                tempSoftLimit, tempGracePeriod);
        FileStorageUtils.createCifsShare(this.fileSystemId, shareName, shareComment, null);
        return FileStorageUtils.getFileSystem(this.fileSystemId);
    }

    public void setFileSystemShareACL() {
        if (fileSystemShareACLs != null && fileSystemShareACLs.length > 0) {
            FileStorageUtils.setFileSystemShareACL(this.fileSystemId, shareName, fileSystemShareACLs);
        }
    }
}

/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.FILESYSTEMS;
import static com.emc.sa.service.ServiceParams.SHARE_COMMENT;
import static com.emc.sa.service.ServiceParams.SUBDIRECTORY;
import static com.emc.sa.service.ServiceParams.VOLUME_NAME;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("CreateFileSystemShare")
public class CreateFileSystemShareService extends ViPRService {

    @Param(FILESYSTEMS)
    protected URI fileSystemId;

    @Param(VOLUME_NAME)
    protected String shareName;

    @Param(SHARE_COMMENT)
    protected String shareComment;

    @Param(value = SUBDIRECTORY, required = false)
    protected String subDirectory;

    @Bindable(itemType = FileStorageUtils.FileSystemACLs.class)
    protected FileStorageUtils.FileSystemACLs[] fileSystemShareACLs;

    @Override
    public void precheck() throws Exception {
        if (fileSystemShareACLs != null && fileSystemShareACLs.length > 0) {
            List<String> invalidNames = FileStorageUtils.getInvalidFileACLs(fileSystemShareACLs);
            if (!invalidNames.isEmpty()) {
                ExecutionUtils.fail("failTask.CreateCifsShareHelper.invalidName", invalidNames, invalidNames);
            }
            fileSystemShareACLs = FileStorageUtils.clearEmptyFileACLs(fileSystemShareACLs);
        }
    }

    @Override
    public void execute() throws Exception {
        FileStorageUtils.createCifsShare(fileSystemId, shareName, shareComment, subDirectory);
        clearRollback();
        if (fileSystemShareACLs != null && fileSystemShareACLs.length > 0) {
            FileStorageUtils.setFileSystemShareACL(fileSystemId, shareName, fileSystemShareACLs);
        }
    }
}

/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.SHARE_COMMENT;
import static com.emc.sa.service.ServiceParams.SHARE_NAME;
import static com.emc.sa.service.ServiceParams.SNAPSHOT;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("ShareFileSnapshot")
public class ShareFileSnapshotService extends ViPRService {
	
	@Param(SNAPSHOT)
    protected String snapshotId;
	
	@Param(SHARE_NAME)
    protected String shareName;

    @Param(SHARE_COMMENT)
    protected String shareComment;

    @Bindable(itemType = FileStorageUtils.FileSystemACLs.class)
    protected FileStorageUtils.FileSystemACLs[] fileSnapshotShareACLs;

    @Override
    public void execute() {
        FileStorageUtils.shareFileSnapshot(uri(snapshotId), shareName, shareComment);
        if (fileSnapshotShareACLs != null && fileSnapshotShareACLs.length > 0) {
            FileStorageUtils.setFileSnapshotShareACL(uri(snapshotId), shareName, fileSnapshotShareACLs);
        }
    }
}

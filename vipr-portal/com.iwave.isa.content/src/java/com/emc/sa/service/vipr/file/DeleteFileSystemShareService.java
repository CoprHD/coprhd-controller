/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.FILESYSTEMS;
import static com.emc.sa.service.ServiceParams.SHARE_NAME;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("DeleteFileSystemShare")
public class DeleteFileSystemShareService extends ViPRService {

    @Param(FILESYSTEMS)
    protected URI fileSystemId;

    @Param(SHARE_NAME)
    protected List<String> shareNames;

    @Override
    public void execute() throws Exception {
        for (String shareName : shareNames) {
            FileStorageUtils.deactivateCifsShare(fileSystemId, shareName);
        }
    }

}
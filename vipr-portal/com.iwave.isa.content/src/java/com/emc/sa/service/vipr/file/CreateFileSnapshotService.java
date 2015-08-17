/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.FILESYSTEMS;
import static com.emc.sa.service.ServiceParams.NAME;

import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.file.FileShareRestRep;

@Service("CreateFileSnapshot")
public class CreateFileSnapshotService extends ViPRService {
    @Param(FILESYSTEMS)
    protected List<String> fileSystemIds;
    @Param(NAME)
    protected String name;

    private List<FileShareRestRep> fileSystems;

    @Override
    public void precheck() {
        fileSystems = FileStorageUtils.getFileSystems(uris(fileSystemIds));
    }

    @Override
    public void execute() {
        for (FileShareRestRep fs : fileSystems) {
            FileStorageUtils.createFileSnapshot(fs.getId(), name);
        }
    }
}

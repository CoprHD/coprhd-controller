/*
 * Copyright (c) 2012-2017 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.FILESYSTEMS;

import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.file.tasks.CheckFileSystemReductionSize;
import com.emc.storageos.model.file.FileShareRestRep;

@Service("NasReduceFileSystem")
public class ReduceFileSystemService extends ViPRService {
	@Param(ServiceParams.SIZE_IN_GB)
    protected Double sizeInGb;

    @Param(FILESYSTEMS)
    protected List<String> fileSystemIds;

    private List<FileShareRestRep> fileSystems;

    @Override
    public void precheck() {
        fileSystems = FileStorageUtils.getFileSystems(uris(fileSystemIds));
        execute(new CheckFileSystemReductionSize(fileSystems, sizeInGb));
    }

    @Override
    public void execute() {
        for (FileShareRestRep fs : fileSystems) {
            FileStorageUtils.reduceFileSystem(fs.getId(), sizeInGb);
        }
    }
}

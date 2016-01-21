/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.FILESYSTEMS;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.OPLOCK;
import static com.emc.sa.service.ServiceParams.SECURITY_STYLE;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.GRACE_PERIOD;
import static com.emc.sa.service.ServiceParams.ADVISORY_LIMIT;
import static com.emc.sa.service.ServiceParams.SOFT_LIMIT;



import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.FileShareRestRep;



public class CreateFileSystemQuotaDirectoryHelper {
    @Param(FILESYSTEMS)
    protected List<String> fileSystemIds;

    @Param(NAME)
    protected String name;
    @Param(OPLOCK)
    protected Boolean oplock;
    @Param(SECURITY_STYLE)
    protected String securityStyle;
    @Param(SIZE_IN_GB)
    protected String size;
    
    @Param(value=SOFT_LIMIT,required=false)
    protected Integer softLimit;
    @Param(value=ADVISORY_LIMIT,required=false)
    protected Integer advisoryLimit;
    @Param(value=GRACE_PERIOD,required=false)
    protected Integer gracePeriod;

    

    private List<FileShareRestRep> fileSystems;

    public void precheck() {
        fileSystems = FileStorageUtils.getFileSystems(ViPRExecutionTask.uris(fileSystemIds));
    }

    public void createFileSystemQuotaDirectories() {
        for (FileShareRestRep fs : fileSystems) {
            URI fsId = fs.getId();
            FileStorageUtils.createFileSystemQuotaDirectory(fsId, name, oplock, securityStyle, size);
        }
    }
}

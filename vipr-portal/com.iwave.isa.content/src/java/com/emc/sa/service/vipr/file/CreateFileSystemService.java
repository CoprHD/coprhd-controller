/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.VOLUME_NAME;
import static com.emc.sa.service.ServiceParams.GRACE_PERIOD;
import static com.emc.sa.service.ServiceParams.ADVISORY_LIMIT;
import static com.emc.sa.service.ServiceParams.SOFT_LIMIT;
import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("CreateFileSystem")
public class CreateFileSystemService extends ViPRService {
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
    
    @Param(value=SOFT_LIMIT,required=false)
    protected Integer softLimit;
    
    @Param(value=ADVISORY_LIMIT,required=false)
    protected Integer advisoryLimit;
    
    @Param(value=GRACE_PERIOD,required=false)
    protected Integer gracePeriod;


    @Override
    public void execute() throws Exception {
        int tempSoftLimit=(softLimit!=null)?softLimit:0;
        int tempAdvisoryLimit=(advisoryLimit!=null)?advisoryLimit:0; 
        int tempGracePeriod=(gracePeriod!=null)?gracePeriod:0;
        FileStorageUtils.createFileSystem(project, virtualArray, virtualPool, shareName, sizeInGb, tempAdvisoryLimit, tempSoftLimit, tempGracePeriod);
    }
}
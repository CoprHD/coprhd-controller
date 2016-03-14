/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.ADVISORY_LIMIT;
import static com.emc.sa.service.ServiceParams.COMMENT;
import static com.emc.sa.service.ServiceParams.GRACE_PERIOD;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.SOFT_LIMIT;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.VOLUME_NAME;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("NasNfsCreateStorage")
public class CreateNfsExportService extends ViPRService {

    @Param(VIRTUAL_POOL)
    protected URI virtualPool;
    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;
    @Param(PROJECT)
    protected URI project;
    @Param(COMMENT)
    protected String comment;
    @Param(SIZE_IN_GB)
    protected Double sizeInGb;
    @Param(VOLUME_NAME)
    protected String exportName;
    @Param(value = SOFT_LIMIT, required = false)
    protected Double softLimit;
    @Param(value = ADVISORY_LIMIT, required = false)
    protected Double advisoryLimit;
    @Param(value = GRACE_PERIOD, required = false)
    protected Double gracePeriod;

    @Bindable(itemType = FileStorageUtils.FileExportRule.class)
    protected FileStorageUtils.FileExportRule[] exportRules;

    @Override
    public void precheck() throws Exception {
        if (exportRules == null || exportRules.length == 0) {
            ExecutionUtils.fail("failTask.CreateFileSystemExport.precheck", new Object[] {}, new Object[] {});
        }
    }

    @Override
    public void execute() {
        int tempSoftLimit = (softLimit != null) ? softLimit.intValue() : 0;
        int tempAdvisoryLimit = (advisoryLimit != null) ? advisoryLimit.intValue() : 0;
        int tempGracePeriod = (gracePeriod != null) ? gracePeriod.intValue() : 0;

        URI fileSystemId = FileStorageUtils.createFileSystem(project, virtualArray, virtualPool, exportName, sizeInGb, tempAdvisoryLimit,
                tempSoftLimit, tempGracePeriod);
        if (exportRules != null) {
            FileStorageUtils.createFileSystemExport(fileSystemId, comment, exportRules[0], null);
            if (exportRules.length > 1) {
                FileStorageUtils.updateFileSystemExport(fileSystemId, null, exportRules);
            }
        }
    }
}

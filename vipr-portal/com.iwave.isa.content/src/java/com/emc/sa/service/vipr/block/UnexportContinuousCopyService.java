/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.EXPORT;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.COPY;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("UnexportContinuousCopy")
public class UnexportContinuousCopyService extends ViPRService {
    
    @Param(PROJECT)
    protected URI projectId;

    @Param(COPY)
    protected URI continuousCopyId;

    @Param(EXPORT)
    protected URI exportId;

    @Override
    public void execute() throws Exception {
        BlockStorageUtils.removeBlockResourceFromExport(continuousCopyId, exportId);
    }

}

/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.COPIES;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("RestoreFromFullCopy")
public class RestoreFromFullCopyService extends ViPRService {

    @Param(COPIES)
    protected String copyId;
    
    @Override
    public void execute() throws Exception {
        BlockStorageUtils.restoreFromFullCopy(uri(copyId));
    }

}

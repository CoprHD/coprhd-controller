/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.COPIES;

import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("DetachFullCopy")
public class DetachFullCopyService extends ViPRService {

    @Param(COPIES)
    protected List<String> copyIds;
    
    @Override
    public void execute() throws Exception {
        BlockStorageUtils.detachFullCopies(uris(copyIds));
    }

}

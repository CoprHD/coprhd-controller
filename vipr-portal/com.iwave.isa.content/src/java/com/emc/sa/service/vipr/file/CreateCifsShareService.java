/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("NasCifsCreateStorage")
public class CreateCifsShareService extends ViPRService {
    @Bindable
    private CreateCifsShareHelper createCifsShareHelper = new CreateCifsShareHelper();

    @Override
    public void precheck() throws Exception{
        createCifsShareHelper.precheckFileACLs();
    }
    
    @Override
    public void execute() {
        createCifsShareHelper.createCifsShare();
        clearRollback();
        createCifsShareHelper.setFileSystemShareACL();
    }
}

/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.custom.tasks;

import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.custom.CustomDataObjectRestRep;
import com.emc.vipr.client.Task;

@Service("CustomActivityFlowSample")
public class CustomActivityFlowSampleService extends ViPRService {


    
    @Override
    public void init() throws Exception {
        super.init();
    }

      
    
    @Override
    public void execute() throws Exception {
    	 Task<CustomDataObjectRestRep> task = execute(new CustomActivityFlowSample("Activiti Flow"));
    }

}
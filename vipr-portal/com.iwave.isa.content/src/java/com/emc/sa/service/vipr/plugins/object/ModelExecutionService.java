package com.emc.sa.service.vipr.plugins.object;


import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionContext;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.extension.ExternalTaskParams;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.block.CreateBlockVolumeForHostHelper;
import com.emc.sa.service.vipr.block.BlockStorageUtils.VolumeTable;
import com.google.common.collect.Lists;

@Service("ModelExecution")
public class ModelExecutionService extends ViPRService  {

  
    @Override
    public void init() throws Exception {
        super.init();
    }
    
	@Override
	public void execute() throws Exception {

	}

	
    @Override
    public void executeModelWorkflow() throws Exception{
    	super.executeModelWorkflow();
    }
	
    

    @Override
    public void precheck() throws Exception {
    	
    }

	public void preLaunch() throws Exception  {
		
	}
	

	public void postLaunch() throws Exception {
		
	}

	public void postcheck() throws Exception {
		
	}

}

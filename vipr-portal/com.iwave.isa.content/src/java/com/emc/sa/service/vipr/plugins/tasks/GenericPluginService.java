package com.emc.sa.service.vipr.plugins.tasks;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.execute;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

public class GenericPluginService extends ViPRService {

	


    @Bindable(itemType = GenericPluginParams.class)
    protected GenericPluginParams genericPluginParams;

    
	GenericPluginServiceHelper genericPluginServiceHelper = new GenericPluginServiceHelper();

    
    @Override
    public void init() throws Exception {
        super.init();

            BindingUtils.bind(genericPluginServiceHelper, GenericPluginUtils.createParam(genericPluginParams));
    }
    
	@Override
	public void execute() throws Exception {
        Tasks<GenericRestRep> tasks = execute(new  GenericTaskExecuteor(genericPluginServiceHelper));
	        
		
	}

}

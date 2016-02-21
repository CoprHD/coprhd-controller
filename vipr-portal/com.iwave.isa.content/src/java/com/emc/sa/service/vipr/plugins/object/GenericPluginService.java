package com.emc.sa.service.vipr.plugins.object;


import com.emc.sa.engine.ExecutionContext;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.extension.ExternalTaskParams;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("GenericPlugin")
public class GenericPluginService extends ViPRService  {

	GenericPluginServiceHelper genericPluginServiceHelper = new GenericPluginServiceHelper();
    
    @Override
    public void init() throws Exception {
        super.init();
        //genericExtensionTaskParams.setParams(externalParam);
        //BindingUtils.bind(genericPluginServiceHelper, GenericPluginUtils.createParam(genericExtensionTaskParams));
    }
    
	@Override
	public void execute() throws Exception {
		
		ExternalTaskParams genericExtensionTaskParams = new ExternalTaskParams();
		ExecutionContext context = ExecutionUtils.currentContext();
		genericExtensionTaskParams.setExternalParam((String)context.getParameters().get("externalParam"));
		
		 GenericPluginUtils.executeExtenstionTask(genericExtensionTask,genericExtensionTaskParams);
		
	}



}

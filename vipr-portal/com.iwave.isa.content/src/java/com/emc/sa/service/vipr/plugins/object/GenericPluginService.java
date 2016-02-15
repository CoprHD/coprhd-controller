package com.emc.sa.service.vipr.plugins.object;


import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.ExternalTaskApdapterInterface;
import com.emc.sa.engine.service.ExternalTaskExecutor;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("GenericPlugin")
public class GenericPluginService extends ViPRService implements ExternalTaskExecutor {

	
	@Param("externalParam")
	protected String externalParam;
	
	
	private ExternalTaskApdapterInterface genericExtensionTask;

	@Override
	public void setGenericExtensionTask(ExternalTaskApdapterInterface genericExtensionTask) {
		this.genericExtensionTask = genericExtensionTask;
	}

	//@Bindable(itemType = ExternalTaskParams.class)
    //protected ExternalTaskParams genericExtensionTaskParams;
	
	
    
	GenericPluginServiceHelper genericPluginServiceHelper = new GenericPluginServiceHelper();
    
    @Override
    public void init() throws Exception {
        super.init();
        //genericExtensionTaskParams.setParams(externalParam);
        //BindingUtils.bind(genericPluginServiceHelper, GenericPluginUtils.createParam(genericExtensionTaskParams));
    }
    
	@Override
	public void execute() throws Exception {
        //Tasks<GenericRestRep> tasks = execute(new  GenericTaskExecuteor(genericPluginServiceHelper));
		//Tasks<GenericRestRep> tasks = (Tasks<GenericRestRep>) execute(genericExtensionTask);
		
		ExternalTaskParams genericExtensionTaskParams = new ExternalTaskParams();
		genericExtensionTaskParams.setParams(externalParam);
		
		 GenericPluginUtils.executeExtenstionTask(genericExtensionTask,genericExtensionTaskParams);
		
	}



}

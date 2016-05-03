/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.NAME;





import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;

@Service("ViewVolumesByProject")
public class ViewVolumesByProjectService extends ViPRService {
	@Param(NAME)
    protected String name;
	protected URI pId;
	protected URI vId;
	
	@Override
    public void execute() {
		System.out.println(name + "myservice test");
		Task<String> task = BlockStorageUtils.simplyTest(name, pId, vId);
	}
}
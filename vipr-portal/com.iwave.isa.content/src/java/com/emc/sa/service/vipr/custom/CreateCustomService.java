/*
 * Copyright (c) 2012-2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.custom;

import static com.emc.sa.service.ServiceParams.NAME;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("CreateCustom")
public class CreateCustomService extends ViPRService {

    @Param(NAME)
    protected String name;

	@Override
	public void execute() throws Exception {
		// TODO Auto-generated method stub
		
	}

  
  
}

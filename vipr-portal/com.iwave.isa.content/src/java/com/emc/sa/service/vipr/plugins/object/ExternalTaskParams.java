package com.emc.sa.service.vipr.plugins.object;

public class ExternalTaskParams {
	
	String externalParam;
	

	public void setParams(String externalParam) {
		this.externalParam=externalParam;
		
		System.out.println("ExternalTaskParams loaded "+externalParam);
		
	}

}

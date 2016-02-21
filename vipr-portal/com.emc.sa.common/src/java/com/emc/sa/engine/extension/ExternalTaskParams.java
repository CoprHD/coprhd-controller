package com.emc.sa.engine.extension;

public class ExternalTaskParams {
	
	String externalParam;
	

	public void setExternalParam(String externalParam) {
		this.externalParam = externalParam;
		System.out.println("ExternalTaskParams loaded "+externalParam);
	}


	public String getExternalParam() {
		return externalParam;
	}


//	public void setParams(String externalParam) {
//		this.externalParam=externalParam;
//		
//		System.out.println("ExternalTaskParams loaded "+externalParam);
//		
//	}

}

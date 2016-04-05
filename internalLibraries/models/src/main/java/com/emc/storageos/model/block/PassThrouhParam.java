package com.emc.storageos.model.block;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;


//@XmlRootElement(name = "passthrouh_params")
public abstract class PassThrouhParam {
	

	
//	private String passThrouhParams;
//
//	public PassThrouhParam(String passThrouhParams) {
//		this.passThrouhParams = passThrouhParams;
//	}
//
//	public PassThrouhParam() {
//		
//	}
//
//	@XmlElement(name="passthrouh_params")
//	public String getPassThrouhParams() {
//		return passThrouhParams;
//	}
//
//	public void setPassThrouhParams(String passThrouhParams) {
//		this.passThrouhParams = passThrouhParams;
//	}

	
	private Map<String, String> passThrouhParams ;

	
	public PassThrouhParam() {
		passThrouhParams = new HashMap<String, String>();
	}
	
    public PassThrouhParam(Map<String, String> passThrouhParams) {
    	this.passThrouhParams = passThrouhParams;
	}

 	@XmlElement(name="passthrouh_params")
	public Map<String, String> getPassThrouhParams() {
		return passThrouhParams;
	}

	public void setPassThrouhParams(Map<String, String> passThrouhParams) {
		this.passThrouhParams = passThrouhParams;
	}
	 
	abstract void reverseMapPassThroughParams();


}

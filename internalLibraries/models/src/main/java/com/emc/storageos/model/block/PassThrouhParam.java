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
	private String exportPassThroughParam ;

	
	public PassThrouhParam() {
		passThrouhParams = new HashMap<String, String>();
	}
	
    public PassThrouhParam(Map<String, String> passThrouhParams) {
    	this.passThrouhParams = passThrouhParams;
	}
    
    public PassThrouhParam(String exportPassThroughParam) {
    	this.exportPassThroughParam = exportPassThroughParam;
	}

 	@XmlElement(name="passthrouh_params")
	public Map<String, String> getPassThrouhParams() {
		return passThrouhParams;
	}
 	
 	@XmlElement(name="exportPassThroughParam")
	public String getExportPassThroughParam() {
		return exportPassThroughParam;
	}

	public void setPassThrouhParams(Map<String, String> passThrouhParams) {
		this.passThrouhParams = passThrouhParams;
	}
	
	public void setExportPassThroughParam(String exportPassThroughParam) {
		this.exportPassThroughParam = exportPassThroughParam;
	}
	 
	abstract public void reverseMapPassThroughParams();


}

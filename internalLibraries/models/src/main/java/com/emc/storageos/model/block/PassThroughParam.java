package com.emc.storageos.model.block;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;


//@XmlRootElement(name = "passthrough_params")
public abstract class PassThroughParam {
	

	
//	private String passThroughParams;
//
//	public PassThroughParam(String passThroughParams) {
//		this.passThroughParams = passThroughParams;
//	}
//
//	public PassThroughParam() {
//		
//	}
//
//	@XmlElement(name="passthrough_params")
//	public String getPassThroughParams() {
//		return passThroughParams;
//	}
//
//	public void setPassThroughParams(String passThroughParams) {
//		this.passThroughParams = passThroughParams;
//	}

	
	private Map<String, String> passThroughParams ;
	private String exportPassThroughParam ;

	
	public PassThroughParam() {
		passThroughParams = new HashMap<String, String>();
	}
	
    public PassThroughParam(Map<String, String> passThroughParams) {
    	this.passThroughParams = passThroughParams;
	}
    
    public PassThroughParam(String exportPassThroughParam) {
    	this.exportPassThroughParam = exportPassThroughParam;
	}

 	@XmlElement(name="passthrough_params")
	public Map<String, String> getPassThroughParams() {
		return passThroughParams;
	}
 	
 	@XmlElement(name="exportPassThroughParam")
	public String getExportPassThroughParam() {
		return exportPassThroughParam;
	}

	public void setPassThroughParams(Map<String, String> passThroughParams) {
		this.passThroughParams = passThroughParams;
	}
	
	public void setExportPassThroughParam(String exportPassThroughParam) {
		this.exportPassThroughParam = exportPassThroughParam;
	}
	 
	abstract public void reverseMapPassThroughParams();


}

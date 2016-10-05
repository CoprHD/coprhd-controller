package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Cf("ClassOfService")
public class ClassOfService extends DataObject{
	

	Map<String,Object> basicProfile = new HashMap<String,Object>();
	Map<String,Object> protectionProfile = new HashMap<String,Object>();
	Map<String,Object> highavailabilityProfile = new HashMap<String,Object>();
	Map<String,Object> customProfile = new HashMap<String,Object>();
	
	@Name("basicProfile")
	public Map<String, Object> getBasicProfile() {
		return basicProfile;
	}
	
	public void setBasicProfile(Map<String, Object> basicProfile) {
		this.basicProfile = basicProfile;
	}
	
	@Name("protectionProfile")
	public Map<String, Object> getProtectionProfile() {
		return protectionProfile;
	}
	
	public void setProtectionProfile(Map<String, Object> protectionProfile) {
		this.protectionProfile = protectionProfile;
	}
	
	@Name("highavailabilityProfile")
	public Map<String, Object> getHighavailabilityProfile() {
		return highavailabilityProfile;
	}
	
	public void setHighavailabilityProfile(Map<String, Object> highavailabilityProfile) {
		this.highavailabilityProfile = highavailabilityProfile;
	}
	
	@Name("customProfile")
	public Map<String, Object> getCustomProfile() {
		return customProfile;
	}
	
	public void setCustomProfile(Map<String, Object> customProfile) {
		this.customProfile = customProfile;
	}

	

}

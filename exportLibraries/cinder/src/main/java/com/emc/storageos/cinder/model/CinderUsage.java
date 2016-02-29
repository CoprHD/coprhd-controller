/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@JsonRootName(value="quota_set")
@XmlRootElement(name="quota_set")
public class CinderUsage {	
		private Map<String, UsageAndLimits> quota_set = new HashMap<String, UsageAndLimits>();

		public Map<String, UsageAndLimits> getQuota_set() {
			return quota_set;
		}

		public void setQuota_set(Map<String, UsageAndLimits> quota_set) {
			this.quota_set = quota_set;
		}    	    		
}

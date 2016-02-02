/* Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.codehaus.jackson.map.annotate.JsonRootName;

@JsonRootName(value = "quota_class_set")
@XmlRootElement(name = "quota_class_set")
public class CinderQuotaClassDetails {
	
	 /**
     * Json model representation for CinderQuotaClassDetails
     * 
     *{"quota_class_set": {
     	"gigabytes_ViPR-VMAX": -1, "snapshots_ViPR-VMAX": -1, 
		"snapshots": 10, "volumes_ViPR-VMAX": -1, 
		"snapshots_vnx-vpool-1": 102,     			//						
		"gigabytes_vnx-vpool-1": 102, 
		"volumes_vnx-vpool-1": 102, 
		"gigabytes": 1000, 
		"gigabytes_vt-1": -1, 
		"volumes": 10 }
		}
     */
	
    @XmlElement(name = "quota_class_set")
    public Map<String, String> quota_class_set = new HashMap<String, String>();
         
    public Map<String, String> getQuotaClass_set() {
        return quota_class_set;
    }

}

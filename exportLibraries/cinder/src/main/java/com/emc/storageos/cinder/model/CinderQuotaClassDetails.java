/* Copyright (c) 2015 EMC Corporation
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
	

    @XmlElement(name = "quota_class_set")
    public Map<String, String> quota_class_set = new HashMap<String, String>();
    
    public String class_name; 

    public Map<String, String> getQuotaClass_set() {
        return quota_class_set;
    }

}

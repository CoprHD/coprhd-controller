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

@JsonRootName(value = "quota_set")
@XmlRootElement(name = "quota_set")
public class CinderQuotaDetails {

    @XmlElement(name = "quota_set")
    public Map<String, String> quota_set = new HashMap<String, String>();

    public Map<String, String> getQuota_set() {
        return quota_set;
    }

}

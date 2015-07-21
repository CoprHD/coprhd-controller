/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geomodel;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;


@XmlRootElement
public class VdcPreCheckParam {
    private Boolean fresher;
    private String configChangeType;
    private String softwareVersion;
    
    @XmlElement(name="fresher")
    public Boolean getFresher() {
        return fresher;
    }
    
    public void setFresher(Boolean isFresher) {
        this.fresher = isFresher;
    }
    
    @XmlElement(name="config_change_type")
         public String getConfigChangeType() {
        return configChangeType;
    }

    public void setConfigChangeType(String configChangeType) {
        this.configChangeType = configChangeType;
    }

    @XmlElement(name="software_version")
    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }
}

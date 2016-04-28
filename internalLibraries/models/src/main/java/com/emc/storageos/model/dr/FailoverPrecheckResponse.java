/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "failover_precheck_error")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class FailoverPrecheckResponse extends SiteErrorResponse {
    
    private SiteRestRep site;

    @XmlElement(name = "site")
    public SiteRestRep getSite() {
        return site;
    }

    public void setSite(SiteRestRep site) {
        this.site = site;
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SiteErrorResponse [creationTime=");
        builder.append(this.getCreationTime());
        builder.append(", serviceCode=");
        builder.append(this.getServiceCode());
        builder.append(", serviceCodeName=");
        builder.append(this.getServiceCodeName());
        builder.append(", errorMessage=");
        builder.append(this.getErrorMessage());
        builder.append(", site=");
        builder.append(site);
        builder.append("]");
        return builder.toString();
    }
}

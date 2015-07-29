/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.tenant;

import com.emc.storageos.model.RestLinkRep;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

@XmlRootElement(name = "tenant_info")
public class TenantResponse {
    private URI tenant;
    private String name;
    private RestLinkRep selfLink;

    public TenantResponse() {
    }

    public TenantResponse(URI tenant, String name, RestLinkRep selfLink) {
        this.tenant = tenant;
        this.name = name;
        this.selfLink = selfLink;
    }

    /**
     * Tenant URI
     * 
     * @valid none
     */
    @XmlElement(name = "id")
    public URI getTenant() {
        return tenant;
    }

    public void setTenant(URI tenant) {
        this.tenant = tenant;
    }

    /**
     * Tenant name
     * 
     * @valid none
     */
    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Link to itself (tenant)
     * 
     * @valid none
     */
    @XmlElement(name = "link")
    public RestLinkRep getSelfLink() {
        return selfLink;
    }

    public void setSelfLink(RestLinkRep selfLink) {
        this.selfLink = selfLink;
    }
}

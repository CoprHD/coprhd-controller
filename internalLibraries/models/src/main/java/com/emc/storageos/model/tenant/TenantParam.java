/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.tenant;

import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

import java.net.URI;

public abstract class TenantParam {

    private String description;
    private URI webStorageDefaultProject;
    private URI webStorageDefaultVpool;

    public TenantParam() {}
    
    public TenantParam(String description, URI webStorageDefaultProject,
            URI webStorageDefaultVpool) {
        this.description = description;
        this.webStorageDefaultProject = webStorageDefaultProject;
        this.webStorageDefaultVpool = webStorageDefaultVpool;
    }

    /**
     * Description for the tenant.
     * @valid any string
     */
    @XmlElement(required = false)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Default project URI for this tenant
     * @valid any existing project URI in this tenant
     */
    @XmlElement(name = "web_storage_default_project", required = false)
    @JsonProperty("web_storage_default_project")
    public URI getWebStorageDefaultProject() {
        return webStorageDefaultProject;
    }

    public void setWebStorageDefaultProject(URI webStorageDefaultProject) {
        this.webStorageDefaultProject = webStorageDefaultProject;
    }

    /**
     * Default virtual pool URI for this tenant
     * @valid any existing virtual pool URI
     */
    @XmlElement(name = "web_storage_default_vpool", required = false)
    @JsonProperty("web_storage_default_vpool")
    public URI getWebStorageDefaultVpool() {
        return webStorageDefaultVpool;
    }

    public void setWebStorageDefaultVpool(URI webStorageDefaultVpool) {
        this.webStorageDefaultVpool = webStorageDefaultVpool;
    }
 
}

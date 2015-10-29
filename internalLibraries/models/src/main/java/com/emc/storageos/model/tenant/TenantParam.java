/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.tenant;

import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

import java.net.URI;

public abstract class TenantParam {

    private String description;
    private String namespace;
    private URI webStorageDefaultProject;
    private URI webStorageDefaultVpool;

    public TenantParam() {
    }

    public TenantParam(String description, String namespace, URI webStorageDefaultProject,
            URI webStorageDefaultVpool) {
        this.description = description;
        this.namespace = namespace;
        this.webStorageDefaultProject = webStorageDefaultProject;
        this.webStorageDefaultVpool = webStorageDefaultVpool;
    }

    /**
     * Description for the tenant.
     * 
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
     * Namespace associated to a tenant.
     * 
     * @valid any string
     */
    @XmlElement(required = false)
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets namespace for a Tenant.
     * 
     * @param namespace
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    /**
     * Default project URI for this tenant
     * 
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
     * 
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

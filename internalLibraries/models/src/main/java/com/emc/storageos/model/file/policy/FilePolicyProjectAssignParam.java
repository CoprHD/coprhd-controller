/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.io.Serializable;
import java.net.URI;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * @author jainm15
 */
public class FilePolicyProjectAssignParam implements Serializable {

    private static final long serialVersionUID = 1L;
    private String assigntoAll;
    private URI vpool;
    private Set<URI> assignToProjects;

    public FilePolicyProjectAssignParam() {
        super();
    }

    @XmlElement(name = "vpool", required = true)
    public URI getVpool() {
        return this.vpool;
    }

    public void setVpool(URI vpool) {
        this.vpool = vpool;
    }

    @XmlElement(name = "assign_to_all")
    public String getAssigntoAll() {
        return this.assigntoAll;
    }

    public void setAssigntoAll(String assigntoAll) {
        this.assigntoAll = assigntoAll;
    }

    @XmlElementWrapper(name = "assign_to_projects", required = true)
    @XmlElement(name = "project")
    public Set<URI> getAssigntoProjects() {
        return this.assignToProjects;
    }

    public void setAssigntoProjects(Set<URI> assignToProjects) {
        this.assignToProjects = assignToProjects;
    }

}

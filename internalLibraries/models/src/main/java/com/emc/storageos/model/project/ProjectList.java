/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.project;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "projects")
public class ProjectList {
    private List<NamedRelatedResourceRep> projects;

    public ProjectList() {
    }

    public ProjectList(List<NamedRelatedResourceRep> projects) {
        this.projects = projects;
    }

    /**
     * List of projects
     * 
     * @valid none
     * @return List of projects
     */
    @XmlElement(name = "project")
    public List<NamedRelatedResourceRep> getProjects() {
        if (projects == null) {
            projects = new ArrayList<NamedRelatedResourceRep>();
        }
        return projects;
    }

    public void setProjects(List<NamedRelatedResourceRep> projects) {
        this.projects = projects;
    }
}

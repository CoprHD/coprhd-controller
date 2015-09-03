/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.project;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_projects")
public class ProjectBulkRep extends BulkRestRep {
    private List<ProjectRestRep> projects;

    /**
     * List of projects
     * @valid none
     * @return Projects
     */
    @XmlElement(name = "project")
    public List<ProjectRestRep> getProjects() {
        if (projects == null) {
            projects = new ArrayList<ProjectRestRep>();
        }
        return projects;
    }

    public void setProjects(List<ProjectRestRep> projects) {
        this.projects = projects;
    }

    public ProjectBulkRep() {
    }

    public ProjectBulkRep(List<ProjectRestRep> projects) {
        this.projects = projects;
    }
}

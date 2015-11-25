/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.application;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

/**
 * Response for getting a list of applications
 */
@XmlRootElement(name = "applications")
public class ApplicationList {

    private List<NamedRelatedResourceRep> applications;

    public ApplicationList() {
    }

    public ApplicationList(List<NamedRelatedResourceRep> applications) {
        this.applications = applications;
    }

    /**
     * List of application objects that exist in ViPR. Each
     * application contains an id, name, and link.
     * 
     */
    @XmlElement(name = "application")
    public List<NamedRelatedResourceRep> getApplications() {
        if (applications == null) {
            applications = new ArrayList<NamedRelatedResourceRep>();
        }
        return applications;
    }

    public void setApplications(List<NamedRelatedResourceRep> applications) {
        this.applications = applications;
    }

}

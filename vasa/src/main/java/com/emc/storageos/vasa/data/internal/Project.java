/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/* 
 Copyright (c) 2012 EMC Corporation
 All Rights Reserved

 This software contains the intellectual property of EMC Corporation
 or is licensed to EMC Corporation from third parties.  Use of this
 software and the intellectual property contained therein is expressly
 imited to the terms and conditions of the License Agreement under which
 it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vasa.data.internal;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "Project")
public class Project {

    @XmlElement
    private String id;
    @XmlElement
    private boolean inactive;
    @XmlElement(name = "name")
    private String label;
    @XmlElement
    private String tenantOrg;
    @XmlElement
    private String owner;

    @Override
    public String toString() {
        String s = new String();

        s += "Project\n";
        s += "\tid: " + id + "\n";
        s += "\tinactive: " + Boolean.toString(inactive) + "\n";
        s += "\tlabel: " + label + "\n";
        s += "\ttenantOrg: " + tenantOrg + "\n";
        s += "\towner: " + owner + "\n";
        return s;
    }

    public String getId() {
        return id;
    }

    public boolean isInactive() {
        return inactive;
    }

    public String getLabel() {
        return label;
    }

    public String getTenantOrg() {
        return tenantOrg;
    }

    public String getOwner() {
        return owner;
    }

    @XmlRootElement(name = "projects")
    public static class ProjectList {
        public ProjectList() {
            projectElements = new ArrayList<ProjectListElement>();
        }

        @XmlElementRef
        private List<ProjectListElement> projectElements;

        public List<ProjectListElement> getProjectElements() {
            return projectElements;
        }

    }

    @XmlRootElement(name = "project")
    public static class ProjectListElement {
        @XmlElement
        private String id;

        @XmlElement(name = "name")
        private String label;

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

    }

}

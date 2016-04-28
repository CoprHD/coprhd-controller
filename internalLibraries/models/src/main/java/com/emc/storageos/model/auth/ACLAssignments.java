/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.auth;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.codehaus.jackson.annotate.JsonProperty;

@XmlRootElement(name = "acl_assignments")
public class ACLAssignments {

    /**
     * The returned list of ACL assignments.
     * 
     */
    private List<ACLEntry> assignments;

    public ACLAssignments() {
    }

    public ACLAssignments(List<ACLEntry> assignments) {
        this.assignments = assignments;
    }

    @XmlElement(name = "acl_assignment")
    @JsonProperty("acl")
    public List<ACLEntry> getAssignments() {
        if (assignments == null) {
            assignments = new ArrayList<ACLEntry>();
        }
        return assignments;
    }

    public void setAssignments(List<ACLEntry> assignments) {
        this.assignments = assignments;
    }

}

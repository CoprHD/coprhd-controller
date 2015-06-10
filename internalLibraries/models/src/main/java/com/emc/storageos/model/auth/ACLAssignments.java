/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
     * @valid none
     */
    private List<ACLEntry> assignments;

    public ACLAssignments() {}

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

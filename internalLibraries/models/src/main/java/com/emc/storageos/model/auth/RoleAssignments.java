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

import com.emc.storageos.model.RestLinkRep;

@XmlRootElement(name = "role_assignments_create")
public class RoleAssignments {

    /**
     * Link to this resource.
     * @valid none
     */
    private RestLinkRep selfLink;

    /**
     * List of role assignments that are assigned/being assigned.
     * @valid none
     */
    private List<RoleAssignmentEntry> assignments;

    public RoleAssignments() {}

    @XmlElement(name = "link")
    public RestLinkRep getSelfLink() {
        return selfLink;
    }

    public void setSelfLink(RestLinkRep selfLink) {
        this.selfLink = selfLink;
    }

    @XmlElement(name = "role_assignment")
    @JsonProperty("role_assignments")
    public List<RoleAssignmentEntry> getAssignments() {
        if (assignments == null) {
            assignments = new ArrayList<RoleAssignmentEntry>();
        }
        return assignments;
    }

    public void setAssignments(List<RoleAssignmentEntry> assignments) {
        this.assignments = assignments;
    }
}

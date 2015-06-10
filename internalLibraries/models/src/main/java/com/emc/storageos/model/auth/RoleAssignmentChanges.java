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

@XmlRootElement(name = "role_assignment_change")
public class RoleAssignmentChanges {
    /**
     * List of role assignment entries elements to be added.
     * @valid none
     */
    private List<RoleAssignmentEntry> add;

    /**
     * List of role assignment entries elements to be removed.
     * @valid none
     */
    private List<RoleAssignmentEntry> remove;

    public RoleAssignmentChanges() { }
    public RoleAssignmentChanges(List<RoleAssignmentEntry> add, List<RoleAssignmentEntry> remove) {
        this.add = add;
        this.remove = remove;
    }

    @XmlElement(name = "add")
    public List<RoleAssignmentEntry> getAdd() {
        if (add == null) {
            add = new ArrayList<RoleAssignmentEntry>();
        }
        return add;
    }

    public void setAdd(List<RoleAssignmentEntry> add) {
        this.add = add;
    }

    @XmlElement(name = "remove")
    public List<RoleAssignmentEntry> getRemove() {
        if (remove == null) {
            remove = new ArrayList<RoleAssignmentEntry>();
        }
        return remove;
    }

    public void setRemove(List<RoleAssignmentEntry> remove) {
        this.remove = remove;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("ADD=");
        builder.append(getAdd());

        builder.append(", REMOVE=");
        builder.append(getRemove());

        return builder.toString();
    }
}
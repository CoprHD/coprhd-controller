/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.auth;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "acl_assignment_changes")
public class ACLAssignmentChanges {
    /**
     * List of acl entries to add in this ACL change.
     * 
     * @valid There needs to be at least an add or a remove element.
     */
    private List<ACLEntry> add;

    /**
     * List of acl entries to remove in this ACL change.
     * 
     * @valid There needs to be at least an add or a remove element.
     */
    private List<ACLEntry> remove;

    public ACLAssignmentChanges() {
    }

    public ACLAssignmentChanges(List<ACLEntry> add, List<ACLEntry> remove) {
        this.add = add;
        this.remove = remove;
    }

    @XmlElement(name = "add")
    public List<ACLEntry> getAdd() {
        if (add == null) {
            add = new ArrayList<ACLEntry>();
        }
        return add;
    }

    public void setAdd(List<ACLEntry> add) {
        this.add = add;
    }

    @XmlElement(name = "remove")
    public List<ACLEntry> getRemove() {
        if (remove == null) {
            remove = new ArrayList<ACLEntry>();
        }
        return remove;
    }

    public void setRemove(List<ACLEntry> remove) {
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
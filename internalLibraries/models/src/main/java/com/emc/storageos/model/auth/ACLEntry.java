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

public class ACLEntry {

    /**
     * List of privileges that make up this entry.  Some privileges will apply to different APIs.
     * @valid ALL: allows all provisioning operations, everything under a project (fs, volume, snapshots etc)
     * @valid BACKUP: allows all snapshot related operations (create/delete/export snapshots)
     * @valid USE: can use a virtual array or virtual pool
     */
    private List<String> aces;

    /**
     * The username to which the privilege is assigned or being assigned/revoked
     * If subject_id is specified, group may not be specified.
     * @valid One of subject_id, group or tenant must be specified.
     * @valid example: username@company.com
     */
    private String subjectId;

    /**
     * The group to which the privilege is assigned or being assigned/revoked.
     * If group is specified, subject_id may not be specified.
     * @valid One of subject_id, group or tenant must be specified.
     * @valid example: group@company.com
     */
    private String group;

    /**
     * Tenant id inside of which this entry applies
     * @valid One of subject_id, group or tenant must be specified.
     * @valid example:  urn:storageos:TenantOrg:346e95bd-3d66-4980-b87a-84b997c0ab20:
     */
    private String tenant;

    public ACLEntry() {}

    public ACLEntry(List<String> aces, String subjectId, String group, String tenant) {
        this.aces = aces;
        this.subjectId = subjectId;
        this.group = group;
        this.tenant = tenant;
    }

    @XmlElement(name = "privilege", required = true)
    public List<String> getAces() {
        if (aces == null) {
            aces = new ArrayList<String>();
        }
        return aces;
    }

    public void setAces(List<String> aces) {
        this.aces = aces;
    }

    @XmlElement(name = "subject_id")
    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String sid) {
        this.subjectId = sid;
    }

    @XmlElement(name = "group")
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    @XmlElement(name = "tenant")
    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        if (subjectId != null) {
            builder.append(subjectId);
        } else if (group != null) {
            builder.append(group);
        } else if (tenant != null) {
            builder.append(tenant);
        }

        builder.append("=");
        builder.append(getAces());

        return builder.toString();
    }
}
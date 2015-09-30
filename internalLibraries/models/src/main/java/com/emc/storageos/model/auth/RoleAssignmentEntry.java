/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.auth;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

public class RoleAssignmentEntry {

    /**
     * Roles to that are part of this assignment.
     * 
     * @valid SYSTEM_ADMIN (virtual data center role)
     * @valid SECURITY_ADMIN (virtual data center role)
     * @valid SYSTEM_MONITOR (virtual data center role)
     * @valid SYSTEM_AUDITOR (virtual data center role)
     * @valid TENANT_ADMIN (tenant role)
     * @valid PROJECT_ADMIN (tenant role)
     * @valid TENANT_APPROVER (tenant role)
     */
    private List<String> roles;

    /**
     * Subject to whom the role is assigned or being assigned/revoked.
     * 
     * @valid Only one of subject_id or group can be supplied.
     * @valid user@company.com
     */
    private String subjectId;

    /**
     * Group to whom the role is assigned or being assigned/revoked
     * 
     * @valid Only one of subject_id or group can be supplied.
     * @valid group@company.com
     */
    private String group;

    public RoleAssignmentEntry() {
    }

    public RoleAssignmentEntry(List<String> roles, String subjectId, String group) {
        this.roles = roles;
        this.subjectId = subjectId;
        this.group = group;
    }

    public RoleAssignmentEntry(String sid, String group) {
        this.subjectId = sid;
        this.group = group;
    }

    @XmlElement(name = "role")
    public List<String> getRoles() {
        if (roles == null) {
            roles = new ArrayList<String>();
        }
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
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

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        if (subjectId != null) {
            builder.append(subjectId);
        } else if (group != null) {
            builder.append(group);
        }

        builder.append("=");
        builder.append(getRoles());

        return builder.toString();
    }
}
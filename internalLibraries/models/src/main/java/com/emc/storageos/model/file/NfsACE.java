/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class NfsACE implements Serializable {

    private static final long serialVersionUID = 1780598964262028652L;
    /*
     * response data attributes.
     * and payload attributes
     */

    private String domain;
    private String user;
    private String type = NfsUserType.user.name();
    private String permissions;
    private String permissionType = NfsPermissionType.ALLOW.name();

    public enum NfsPermissionType {
        ALLOW, DENY
    }

    public enum NfsUserType {
        user, group
    }

    public enum NfsPermission {
        READ, WRITE, EXECUTE
    }

    public enum NfsACLOperationErrorType {
        INVALID_PERMISSION_TYPE, INVALID_PERMISSION, INVALID_USER,
        USER_OR_GROUP_NOT_PROVIDED, USER_AND_GROUP_PROVIDED,
        MULTIPLE_ACES_WITH_SAME_USER_OR_GROUP, INVALID_GROUP,
        FS_PATH_NOT_FOUND, ACL_EXISTS, ACL_NOT_FOUND, ACCESS_TO_SHARE_DENIED,
        SNAPSHOT_FS_SHOULD_BE_READ_ONLY,
        MULTIPLE_DOMAINS_FOUND
    }

    @XmlElement(name = "domain", required = false)
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    @XmlElement(name = "user")
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @XmlElement(name = "type", required = false)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @XmlElement(name = "permissions")
    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    @XmlElement(name = "permission_type")
    public String getPermissionType() {
        return permissionType;
    }

    public void setPermissionType(String permissionType) {
        this.permissionType = permissionType;
    }

    public Set<String> getPermissionSet() {

        String[] permissionArray = this.permissions.split(",");
        return new HashSet<String>(Arrays.asList(permissionArray));

    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("NfsACL [");

        if (domain != null) {
            builder.append("domain=");
            builder.append(domain);
            builder.append(", ");
        }
        if (user != null) {
            builder.append("user=");
            builder.append(user);
            builder.append(", ");
        }
        if (type != null) {
            builder.append("type=");
            builder.append(type);
            builder.append(", ");
        }

        if (permissions != null) {
            builder.append("permissions=");
            builder.append(permissions);
            builder.append(", ");
        }
        if (permissionType != null) {
            builder.append("permission_type=");
            builder.append(permissionType);
        }
        builder.append("]");
        return builder.toString();
    }

}

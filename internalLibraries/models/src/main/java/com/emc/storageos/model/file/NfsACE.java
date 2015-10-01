/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class NfsACE implements Serializable {

    private static final long serialVersionUID = 1780598964262028652L;
    /*
     * Payload attributes
     */

    private String domain;
    private String user;
    private String type;
    private String permission;
    private String permissionType;

    public enum NfsACLOperationType {
        ADD, MODIFY, DELETE
    }

    public enum NfsPermissionType {
        ALLOW, DENY
    }

    public enum NfsPermission {
        READ, CHANGE, FULLCONTROL
    }

    public enum NfsACLOperationErrorType {
        INVALID_PERMISSION_TYPE, INVALID_PERMISSION, INVALID_USER,
        USER_OR_GROUP_NOT_PROVIDED, USER_AND_GROUP_PROVIDED,
        MULTIPLE_ACES_WITH_SAME_USER_OR_GROUP, INVALID_GROUP,
        FS_PATH_NOT_FOUND, ACL_EXISTS, ACL_NOT_FOUND, ACCESS_TO_SHARE_DENIED,
        SNAPSHOT_FS_SHOULD_BE_READ_ONLY,
        MULTIPLE_DOMAINS_FOUND
    }

    @XmlElement(name = "domain")
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

    @XmlElement(name = "permission")
    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    @XmlElement(name = "permission_type", required = false)
    public String getPermissionType() {
        return permissionType;
    }

    public void setPermissionType(String permissionType) {
        this.permissionType = permissionType;
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

        if (permission != null) {
            builder.append("permission=");
            builder.append(permission);
        }
        if (permission != null) {
            builder.append("permission_type=");
            builder.append(permissionType);
        }
        builder.append("]");
        return builder.toString();
    }

    public NfsACE() {

    }

}

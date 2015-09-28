/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

public class NfsACLUpdateParams implements Serializable {

    private static final long serialVersionUID = 8233384633469053218L;
    protected NfsACLs aclsToAdd;
    protected NfsACLs aclsToModify;
    protected NfsACLs aclsToDelete;

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

    public NfsACLUpdateParams() {

    }

    @XmlElement(name = "add")
    public NfsACLs getAclsToAdd() {
        return aclsToAdd;
    }

    @XmlElement(name = "modify")
    public NfsACLs getAclsToModify() {
        return aclsToModify;
    }

    @XmlElement(name = "delete")
    public NfsACLs getAclsToDelete() {
        return aclsToDelete;
    }

    public void setAclsToAdd(NfsACLs aclsToAdd) {
        this.aclsToAdd = aclsToAdd;
    }

    public void setAclsToModify(NfsACLs aclsToModify) {
        this.aclsToModify = aclsToModify;
    }

    public void setAclsToDelete(NfsACLs aclsToDelete) {
        this.aclsToDelete = aclsToDelete;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("NfsACLUpdateParams [aclsToAdd=");
        builder.append(aclsToAdd);
        builder.append(", aclsToModify=");
        builder.append(aclsToModify);
        builder.append(", aclsToDelete=");
        builder.append(aclsToDelete);
        builder.append("]");
        return builder.toString();
    }

    public List<NfsACL> retrieveAllACLs() {

        List<NfsACL> aclList = new ArrayList<NfsACL>();

        if (aclsToAdd != null && aclsToAdd.getNfsACLs() != null
                && !aclsToAdd.getNfsACLs().isEmpty()) {
            aclList.addAll(aclsToAdd.getNfsACLs());
        }

        if (aclsToModify != null && aclsToModify.getNfsACLs() != null
                && !aclsToModify.getNfsACLs().isEmpty()) {
            aclList.addAll(aclsToModify.getNfsACLs());
        }

        if (aclsToDelete != null && aclsToDelete.getNfsACLs() != null
                && !aclsToDelete.getNfsACLs().isEmpty()) {
            aclList.addAll(aclsToDelete.getNfsACLs());
        }

        return aclList;

    }

}

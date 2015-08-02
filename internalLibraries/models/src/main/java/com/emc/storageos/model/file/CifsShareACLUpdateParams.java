/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

public class CifsShareACLUpdateParams implements Serializable {

    private static final long serialVersionUID = -694744547907185157L;

    protected ShareACLs aclsToAdd;
    protected ShareACLs aclsToModify;
    protected ShareACLs aclsToDelete;

    public enum ShareACLOperationType {
        ADD, MODIFY, DELETE
    }

    public enum SharePermissionType {
        ALLOW, DENY
    }

    public enum SharePermission {
        READ, CHANGE, FULLCONTROL
    }

    public enum ShareACLOperationErrorType {
        INVALID_PERMISSION_TYPE, INVALID_PERMISSION, INVALID_USER,
        USER_OR_GROUP_NOT_PROVIDED, USER_AND_GROUP_PROVIDED,
        MULTIPLE_ACES_WITH_SAME_USER_OR_GROUP, INVALID_GROUP,
        SHARE_NOT_FOUND, ACL_EXISTS, ACL_NOT_FOUND, ACCESS_TO_SHARE_DENIED,
        SNAPSHOT_SHARE_SHOULD_BE_READ_ONLY,
        MULTIPLE_DOMAINS_FOUND
    }

    public CifsShareACLUpdateParams() {

    }

    @XmlElement(name = "add")
    public ShareACLs getAclsToAdd() {
        return aclsToAdd;
    }

    @XmlElement(name = "modify")
    public ShareACLs getAclsToModify() {
        return aclsToModify;
    }

    @XmlElement(name = "delete")
    public ShareACLs getAclsToDelete() {
        return aclsToDelete;
    }

    public void setAclsToAdd(ShareACLs aclsToAdd) {
        this.aclsToAdd = aclsToAdd;
    }

    public void setAclsToModify(ShareACLs aclsToModify) {
        this.aclsToModify = aclsToModify;
    }

    public void setAclsToDelete(ShareACLs aclsToDelete) {
        this.aclsToDelete = aclsToDelete;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("CifsShareUpdateParams [aclsToAdd=");
        builder.append(aclsToAdd);
        builder.append(", aclsToModify=");
        builder.append(aclsToModify);
        builder.append(", aclsToDelete=");
        builder.append(aclsToDelete);
        builder.append("]");
        return builder.toString();
    }

    public List<ShareACL> retrieveAllACLs() {

        List<ShareACL> aclList = new ArrayList<ShareACL>();

        if (aclsToAdd != null && aclsToAdd.getShareACLs() != null
                && !aclsToAdd.getShareACLs().isEmpty()) {
            aclList.addAll(aclsToAdd.getShareACLs());
        }

        if (aclsToModify != null && aclsToModify.getShareACLs() != null
                && !aclsToModify.getShareACLs().isEmpty()) {
            aclList.addAll(aclsToModify.getShareACLs());
        }

        if (aclsToDelete != null && aclsToDelete.getShareACLs() != null
                && !aclsToDelete.getShareACLs().isEmpty()) {
            aclList.addAll(aclsToDelete.getShareACLs());
        }

        return aclList;

    }

}

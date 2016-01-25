/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.object;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;

public class BucketACLUpdateParams implements Serializable {

    private static final long serialVersionUID = 8064193023756274122L;

    protected BucketACL aclToAdd;
    protected BucketACL aclToModify;
    protected BucketACL aclToDelete;

    public BucketACLUpdateParams() {

    }

    public enum BucketACLOperationType {
        ADD, MODIFY, DELETE
    }

    public enum BucketPermissions {
        EXECUTE, FULL_CONTROL, DELETE, NONE, READ, PRIVILEGED_WRITE, WRITE, READ_ACL, WRITE_ACL
    }

    public enum BucketACLOperationErrorType {
        INVALID_PERMISSIONS, INVALID_USER,
        USER_OR_GROUP_OR_CUSTOMGROUP_NOT_PROVIDED, USER_AND_GROUP_AND_CUSTOMGROUP_PROVIDED, USER_AND_GROUP_PROVIDED,
        USER_AND_CUSTOMGROUP_PROVIDED, GROUP_AND_CUSTOMGROUP_PROVIDED,
        MULTIPLE_ACES_WITH_SAME_USER_OR_GROUP_CUSTOMGROUP, INVALID_GROUP, INVALID_CUSTOMGROUP,
        ACL_EXISTS, ACL_NOT_FOUND, MULTIPLE_DOMAINS_FOUND
    }

    @XmlElement(name = "add")
    public BucketACL getAclToAdd() {
        return aclToAdd;
    }

    public void setAclToAdd(BucketACL aclToAdd) {
        this.aclToAdd = aclToAdd;
    }

    @XmlElement(name = "modify")
    public BucketACL getAclToModify() {
        return aclToModify;
    }

    public void setAclToModify(BucketACL aclToModify) {
        this.aclToModify = aclToModify;
    }

    @XmlElement(name = "delete")
    public BucketACL getAclToDelete() {
        return aclToDelete;
    }

    public void setAclToDelete(BucketACL aclToDelete) {
        this.aclToDelete = aclToDelete;
    }

}

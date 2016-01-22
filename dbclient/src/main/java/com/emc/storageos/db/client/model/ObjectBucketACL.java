/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * BucketACL will contain the details of ACL on Bucket.
 * It will hold information about the user, group, customgroup, domain, bucketId etc. mapped to Bucket ACL
 *
 */
@Cf("ObjectBucketACL")
public class ObjectBucketACL extends DataObject {

    protected String user;
    protected String group;
    protected String customGroup;
    protected String domain;
    // Permissions for user or group or customgroup: execute,full_control,delete,none,read,privileged_write,write,read_acl,write_acl
    protected String permissions;
    protected String bucketACLIndex;
    protected String bucketName;
    protected String namespace;
    protected URI bucketId;

    @Name("user")
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
        setChanged("user");
    }

    @Name("group")
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
        setChanged("group");
    }

    @Name("domain")
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
        setChanged("domain");
    }

    @Name("permissions")
    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
        setChanged("permissions");
    }

    @Name("bucketACLIndex")
    @AlternateId("bucketACLIndexTable")
    public String getBucketACLIndex() {
        return bucketACLIndex;
    }

    public void setBucketACLIndex(String bucketACLIndex) {
        this.bucketACLIndex = bucketACLIndex;
        setChanged("bucketACLIndex");
    }

    @Name("bucketName")
    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
        calculateACLIndex();
        setChanged("bucketName");
    }

    @RelationIndex(cf = "RelationIndex", type = Bucket.class)
    @Name("bucketId")
    public URI getBucketId() {
        return bucketId;
    }

    public void setBucketId(URI bucketId) {
        this.bucketId = bucketId;
        setChanged("bucketId");
    }

    @Name("namespace")
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
        setChanged("namespace");
    }

    @Name("customGroup")
    public String getCustomGroup() {
        return customGroup;
    }

    public void setCustomGroup(String customGroup) {
        this.customGroup = customGroup;
        setChanged("customGroup");
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ObjectBucketACL [user=");
        builder.append(user);
        builder.append(", group=");
        builder.append(group);
        builder.append(", customgroup=");
        builder.append(customGroup);
        builder.append(", permissions=");
        builder.append(permissions);
        builder.append(", buckectName=");
        builder.append(bucketName);
        builder.append(", buckectId=");
        builder.append(bucketId);
        builder.append(", namespace=");
        builder.append(namespace);
        builder.append("]");
        return builder.toString();
    }

    public void calculateACLIndex() {
        String userOrGroupOrCustom = this.user;
        if (userOrGroupOrCustom == null) {
            userOrGroupOrCustom = this.group != null ? this.group : this.customGroup;
        }
        StringBuffer aclIndexBuffer = new StringBuffer();

        if (userOrGroupOrCustom != null) {
            if (this.bucketId != null) {
                aclIndexBuffer.append(this.bucketId)
                        .append(this.domain == null ? "" : this.domain)
                        .append(userOrGroupOrCustom.toLowerCase());
                this.setBucketACLIndex(aclIndexBuffer.toString());
            }
        }
    }

}

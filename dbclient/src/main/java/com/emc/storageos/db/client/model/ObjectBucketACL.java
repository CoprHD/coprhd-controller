/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

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

    @Name("user")
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @Name("group")
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    @Name("domain")
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    @Name("permissions")
    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    @Name("bucketACLIndex")
    public String getBucketACLIndex() {
        return bucketACLIndex;
    }

    public void setBucketACLIndex(String bucketACLIndex) {
        this.bucketACLIndex = bucketACLIndex;
    }

    @RelationIndex(cf = "RelationIndex", type = Bucket.class)
    @Name("bucketName")
    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
        calculateACLIndex();
        setChanged("bucketName");
    }

    @Name("namespace")
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Name("customGroup")
    public String getCustomGroup() {
        return customGroup;
    }

    public void setCustomGroup(String customGroup) {
        this.customGroup = customGroup;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("BucketACL [user=");
        builder.append(user);
        builder.append(", group=");
        builder.append(group);
        builder.append(", customgroup=");
        builder.append(customGroup);
        builder.append(", permissions=");
        builder.append(permissions);
        builder.append(", buckectName=");
        builder.append(bucketName);
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
            if (this.bucketName != null) {
                aclIndexBuffer.append(this.bucketName)
                        .append(this.domain == null ? "" : this.domain)
                        .append(userOrGroupOrCustom);
                this.setBucketACLIndex(aclIndexBuffer.toString());
            }
        }
    }

}

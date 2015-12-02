/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.ecs.api;

import java.util.List;

public class ECSBucketACL {

    String bucket;
    String namespace;
    String permission;
    Acl acl;

    public class Acl {
        String owner;
        List<UserAcl> user_acl;
        List<GroupAcl> group_acl;
        List<CustomGroupAcl> customgroup_acl;

        public Acl(List<UserAcl> user_acl, List<GroupAcl> group_acl,
                List<CustomGroupAcl> customgroup_acl, String owner) {
            super();
            this.user_acl = user_acl;
            this.group_acl = group_acl;
            this.customgroup_acl = customgroup_acl;
            this.owner = owner;
        }

    }

    public class UserAcl {
        String user;
        String permission;

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPermission() {
            return permission;
        }

        public void setPermission(String permission) {
            this.permission = permission;
        }
    }

    public class GroupAcl {
        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public String getPermission() {
            return permission;
        }

        public void setPermission(String permission) {
            this.permission = permission;
        }

        String group;
        String permission;
    }

    public class CustomGroupAcl {
        String customgroup;
        String permission;

        public String getCustomgroup() {
            return customgroup;
        }

        public void setCustomgroup(String customgroup) {
            this.customgroup = customgroup;
        }

        public String getPermission() {
            return permission;
        }

        public void setPermission(String permission) {
            this.permission = permission;
        }

    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public Acl getAcl() {
        return acl;
    }

    public void setAcl(Acl acl) {
        this.acl = acl;
    }

}

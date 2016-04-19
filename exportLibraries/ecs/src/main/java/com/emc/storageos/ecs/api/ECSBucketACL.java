/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.ecs.api;

import java.util.List;

public class ECSBucketACL {

    String bucket;
    String namespace;
    Acl acl;

    public class Acl {

        List<UserAcl> user_acl;
        List<GroupAcl> group_acl;
        List<CustomGroupAcl> customgroup_acl;

        public Acl() {

        }

        public List<UserAcl> getUseAcl() {
            return user_acl;
        }

        public void setUserAcl(List<UserAcl> user_acl) {
            this.user_acl = user_acl;
        }

        public List<GroupAcl> getGroupAcl() {
            return group_acl;
        }

        public void setGroupAcl(List<GroupAcl> group_acl) {
            this.group_acl = group_acl;
        }

        public List<CustomGroupAcl> getCustomgroupAcl() {
            return customgroup_acl;
        }

        public void setCustomgroupAcl(List<CustomGroupAcl> customgroup_acl) {
            this.customgroup_acl = customgroup_acl;
        }

    }

    public class UserAcl {
        String user;
        String[] permission;

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String[] getPermission() {
            return permission;
        }

        public void setPermission(String[] permission) {
            this.permission = permission;
        }

        
    }

    public class GroupAcl {
        
        String group;
        String[] permission;
        
        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public String[] getPermission() {
            return permission;
        }

        public void setPermission(String[] permission) {
            this.permission = permission;
        }

    }

    public class CustomGroupAcl {
        String customgroup;
        String[] permission;

        public String getCustomgroup() {
            return customgroup;
        }

        public void setCustomgroup(String customgroup) {
            this.customgroup = customgroup;
        }

        public String[] getPermission() {
            return permission;
        }

        public void setPermission(String[] permission) {
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

    public Acl getAcl() {
        return acl;
    }

    public void setAcl(Acl acl) {
        this.acl = acl;
    }

}

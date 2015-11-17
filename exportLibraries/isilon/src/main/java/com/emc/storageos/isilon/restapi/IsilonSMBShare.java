/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

import java.util.ArrayList;

/*
 * Class representing the isilon smb share object
 * member names should match the key names in json object
 */
public class IsilonSMBShare {
    public class Persona {
        private String type;   // optional
        private String id;     // optional
        private String name;

        public Persona(String account_type, String i, String n) {
            this.type = account_type;
            id = i;
            name = n;
        }

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("( account type: " + type);
            str.append(", account id: " + id);
            str.append(", account name: " + name);
            str.append(")");
            return str.toString();
        }
    }

    public class Permission {

        public static final String PERMISSION_TYPE_ALLOW = "allow";
        public static final String PERMISSION_TYPE_DENY = "deny";
        public static final String PERMISSION_READ = "read";
        public static final String PERMISSION_CHANGE = "change";
        public static final String PERMISSION_FULL = "full";

        private String permission_type; /* "allow"|"deny" */
        private String permission; /* "full"|"change"|"read" */
        private Persona trustee;

        public Permission(String type, String p, Persona tr) {
            permission_type = type;
            permission = p;
            trustee = tr;
        }

        public Permission(String type, String permission, String userOrGroup) {
            this.permission_type = type;
            this.permission = permission;
            this.trustee = new Persona(null, null, userOrGroup);
        }

        public Persona getTrustee() {
            return trustee;
        }

        public String getPermissionType() {
            return permission_type;
        }

        public String getPermission() {
            return permission;
        }

        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("( permission type: " + permission_type);
            str.append(", permission: " + permission);
            str.append(", trustee: " + trustee);
            str.append(")");
            return str.toString();
        }
    }

    private String id;
    private String name;
    private String path;
    private String description;
    private String csc_policy; /* 'manual'|'documents'|'programs'|'none' */
    private Boolean browsable;
    private ArrayList<Permission> permissions;
    private ArrayList<Persona> run_as_root; /* persona */

    // all of the below are optional
    private Boolean allow_execute_always;
    private String directory_create_mask;
    private Boolean strict_locking;
    private Boolean hide_dot_files;
    private String impersonate_guest; /* 'always'|'bad user'|'never' */
    private Boolean strict_flush;
    private Boolean access_based_enumeration;
    private String mangle_byte_start;
    private String file_create_mask;
    private String create_permissions; /* 'default acl'|'inherit mode bits'|'use create mask and mode' */
    private ArrayList<String> mangle_map;
    private String impersonate_user;
    private String change_notify; /* 'all'|'norecurse'|'none' */
    private Boolean oplocks;
    private Boolean allow_delete_readonly;
    private String directory_create_mode;
    private Boolean ntfs_acl_support;
    private String file_create_mode;
    private Boolean access_based_enumeration_root_only;
    private ArrayList<String> host_acl;
    private Boolean inheritable_path_acl; /* true: Apply Windows Default ACLs | false: Do not change existing permissions */

    /**
     * Constructor
     * 
     * @param n Name, also used for display name
     * @param p Path
     * @param desc Description
     * @param host Host to be added in the acls
     */
    public IsilonSMBShare(String n, String p, String desc, String host) {
        name = n;
        path = p;
        description = desc;
        browsable = true;
        ntfs_acl_support = true;
        oplocks = true;
        strict_flush = true;
        host_acl = new ArrayList<String>();
        host_acl.add(host);
        inheritable_path_acl = true;

        // create the share without permission
    }

    public IsilonSMBShare(String n, String p, String desc, String permissionType, String permission) {
        name = n;
        path = p;
        description = desc;
        browsable = true;
        ntfs_acl_support = true;
        oplocks = true;
        strict_flush = true;
        inheritable_path_acl = true;

        // create the share without permission
    }

    public IsilonSMBShare(String n, String p, String desc) {
        name = n;
        path = p;
        description = desc;
        browsable = true;
        ntfs_acl_support = true;
        oplocks = true;
        strict_flush = true;
        permissions = new ArrayList<Permission>();
        inheritable_path_acl = true;

    }

    /**
     * This constructor is used for modifyIsilonShare()
     * 
     * @param shareName
     */
    public IsilonSMBShare(String shareName) {
        name = shareName;
    }

    public ArrayList<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(ArrayList<Permission> permissions) {
        this.permissions = permissions;
    }

    public String getName() {
        return this.name;
    }

    public String getId() {
        return this.id;
    }

    public String getDescription() {
        return this.description;
    }

    public String getPath() {
        return this.path;
    }

    public Boolean getInheritablePathAcl() {
        return this.inheritable_path_acl;
    }

    public void setInheritablePathAcl(Boolean inheritablePathAcl ) {
        this.inheritable_path_acl = inheritablePathAcl;
    }
    
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("Share ( id: " + id);
        str.append(", name: " + name);
        str.append(", path: " + path);
        str.append(", desc: " + description);
        str.append(", permissions: " + permissions);
        str.append(")");
        return str.toString();
    }

}

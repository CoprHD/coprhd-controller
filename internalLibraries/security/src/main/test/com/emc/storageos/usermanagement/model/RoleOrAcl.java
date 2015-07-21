/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.usermanagement.model;

public class RoleOrAcl {
    private String roleName;


    public static RoleOrAcl SecurityAdmin = new RoleOrAcl("SECURITY_ADMIN");
    public static RoleOrAcl SystemAdmin = new RoleOrAcl("SYSTEM_ADMIN");
    public static RoleOrAcl SystemMonitor = new RoleOrAcl("SYSTEM_MONITOR");
    public static RoleOrAcl SystemAuditor = new RoleOrAcl("SYSTEM_AUDITOR");
    public static RoleOrAcl PROXY_USER = new RoleOrAcl("PROXY_USER");
    public static RoleOrAcl TenantAdmin = new RoleOrAcl("TENANT_ADMIN");
    public static RoleOrAcl ProjectAdmin = new RoleOrAcl("PROJECT_ADMIN");
    public static RoleOrAcl ProjectAclOwn = new RoleOrAcl("OWN");
    public static RoleOrAcl ProjectAclAll = new RoleOrAcl("ALL");
    public static RoleOrAcl ProjectAclBackup = new RoleOrAcl("BACKUP");


    private RoleOrAcl(String roleName) {
        this.roleName = roleName;
    }

    public String toString() {
        return getRoleName();
    }

    public String getRoleName() {
        return roleName;
    }

}

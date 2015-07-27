/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authorization;

/**
 *  Global enums for roles
 */
public enum Role {
    // VDC roles
    SECURITY_ADMIN,
    SYSTEM_ADMIN,
    SYSTEM_MONITOR,
    SYSTEM_AUDITOR,
    PROXY_USER,
    // Tenant Roles
    TENANT_ADMIN,
    PROJECT_ADMIN,
    TENANT_APPROVER,

    // Internal VDC roles
    RESTRICTED_SECURITY_ADMIN,
    RESTRICTED_SYSTEM_ADMIN

}


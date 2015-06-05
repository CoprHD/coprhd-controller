/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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


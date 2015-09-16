/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to attach a permission check to a resource method
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface DefaultPermissions {
    // Roles allowed for read access
    Role[] readRoles();

    // ACLs allowed for read access
    ACL[] readAcls() default {};

    // Roles allowed for write access
    Role[] writeRoles();

    // ACLs allowed for read access
    ACL[] writeAcls() default {};
}

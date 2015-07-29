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
    Role[] read_roles();

    // ACLs allowed for read access
    ACL[] read_acls() default {};

    // Roles allowed for write access
    Role[] write_roles();

    // ACLs allowed for read access
    ACL[] write_acls() default {};
}

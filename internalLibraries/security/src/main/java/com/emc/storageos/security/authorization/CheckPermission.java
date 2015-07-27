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
 *  Annotation to attach a permission check to a resource method
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckPermission {
    // Roles allowed
    Role[] roles();
    // ACLs allowed
    ACL[] acls() default {};
    // block proxied users
    boolean block_proxies() default false;
}

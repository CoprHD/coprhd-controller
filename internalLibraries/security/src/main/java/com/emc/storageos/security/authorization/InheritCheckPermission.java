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
 *  Annotation to attach a default permissions check to a resource method
 *  inherits permissions defined by DefaultPermissions annotation on the class
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface InheritCheckPermission {
    /**
     * If true, checks for write access, read access otherwise
     * @return
     */
    boolean write_access()  default false;
}

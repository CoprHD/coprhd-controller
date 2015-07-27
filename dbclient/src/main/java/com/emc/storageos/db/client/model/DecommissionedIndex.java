/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.lang.annotation.*;

/**
 * When marked with this annotation, an inverted index is updated when the
 * boolean field is set to true
 * DataObject type -> DataObject.id
 * and can be queried by using Constraint queries
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DecommissionedIndex {
    String value() default "";
}

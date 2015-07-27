/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.lang.annotation.*;

@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
/**
 * Maps to multiple columns for a multiple value field.  Field values are
 * stored with column name using annotation specified string as a prefix.
 * For example,
 *
 * @Set("capability")
 * public StringSet myField;
 *
 * results in columns like
 *
 * capability:x | capability:y
 * null           null
 *
 * where x and y are values from myField set
 */
public @interface Name {
    String value() default "";
}

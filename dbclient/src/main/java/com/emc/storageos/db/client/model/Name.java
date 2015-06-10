/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.common;

import java.lang.annotation.Annotation;

/**
 * This abstract class is intended only for testing schema changes
 * 
 * It can be used to direct the db schema scanner to ignore specific
 * column families, fields or annotations within a column family when
 * generating the data model.
 */
public abstract class DbSchemaScannerInterceptor {
    public boolean isClassIgnored(String cfName) {
        return false;
    }

    public boolean isFieldIgnored(String cfName, String fieldName) {
        return false;
    }

    public boolean isAnnotationIgnored(String cfName, String fieldName, Class<? extends Annotation> annoType) {
        return false;
    }

    public boolean isClassAnnotationIgnored(String cfName, String annotationName) {
        return false;
    }
}

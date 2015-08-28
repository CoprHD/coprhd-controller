/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.common;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.db.common.DbSchemaInterceptorImpl;

public class DbSchemaInterceptorTest {

    private static final String COLUMN_FAMILY = "Task";
    private static final String IGNORE_FIELD = "error";
    private static final String NORMAL_FIELD = "normal";
    private static final String IGNORE_CLASS_ANNOTATION = "ExcludeFromGarbageCollection";
    private static final String NORMAL_CLASS_ANNOTATION = "Cf";
    private static final String NORMAL_CF = "Volume";
    private static final String IGNORE_CF = "ObjectBaseUrl";

    private static DbSchemaScannerInterceptor interceptor = null;

    // Suppress Sonar violation of Lazy initialization of static fields should be synchronized
    // Junit test will be called in single thread by default, it's safe to ignore this violation
    @SuppressWarnings("squid:S2444")
    @Before
    public void setup() {
        interceptor = new DbSchemaInterceptorImpl();
    }

    @Test
    public void shouldIgnoreSpecificField() {
        Assert.assertTrue(this.interceptor.isFieldIgnored(COLUMN_FAMILY, IGNORE_FIELD));
    }

    @Test
    public void shouldNotIgnoreNormalField() {
        Assert.assertFalse(this.interceptor.isFieldIgnored(COLUMN_FAMILY, NORMAL_FIELD));
    }

    @Test
    public void shouldIgnoreSpecificClsAnnt() {
        Assert.assertTrue(this.interceptor.isClassAnnotationIgnored(COLUMN_FAMILY, IGNORE_CLASS_ANNOTATION));
    }

    @Test
    public void shouldNotIgnoreSpecificClsAnnt() {
        Assert.assertFalse(this.interceptor.isClassAnnotationIgnored(COLUMN_FAMILY, NORMAL_CLASS_ANNOTATION));
    }
    
    @Test
    public void shouldReturnTrueForIgnoredCf() {
    	Assert.assertTrue(this.interceptor.isClassIgnored(IGNORE_CF));
    }
    
    @Test
    public void shouldReturnFalseForNormalCf() {
    	Assert.assertFalse(this.interceptor.isClassIgnored(NORMAL_CF));
    }
}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.common;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.ExcludeFromGarbageCollection;
import com.emc.storageos.db.common.DbSchemaInterceptorImpl;

public class DbSchemaInterceptorTest {
	
	private static final String COLUMN_FAMILY = "Task";
	private static final String IGNORE_FIELD = "error";
	private static final String NORMAL_FIELD = "normal";
	private static final String IGNORE_CLASS_ANNOTATION = "ExcludeFromGarbageCollection";
	private static final String NORMAL_CLASS_ANNOTATION = "Cf";	
	
	private static DbSchemaScannerInterceptor interceptor = null;
	
	@Before
	public void setup(){
		interceptor = new DbSchemaInterceptorImpl();
	}
	
	@Test
	public void shouldIgnoreSpecificField(){
		Assert.assertTrue(this.interceptor.isFieldIgnored(COLUMN_FAMILY, IGNORE_FIELD));
	}
	
	@Test
	public void shouldNotIgnoreNormalField(){
		Assert.assertFalse(this.interceptor.isFieldIgnored(COLUMN_FAMILY, NORMAL_FIELD));
	}
	
	@Test
	public void shouldIgnoreSpecificClsAnnt(){
		Assert.assertTrue(this.interceptor.isClassAnnotationIgnored(COLUMN_FAMILY, IGNORE_CLASS_ANNOTATION));
	}
	
	@Test
	public void shouldNotIgnoreSpecificClsAnnt(){
		Assert.assertFalse(this.interceptor.isClassAnnotationIgnored(COLUMN_FAMILY, NORMAL_CLASS_ANNOTATION));
	}
}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.upgrade.impl;

import java.beans.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

import javassist.*;
import javassist.util.*;
import javassist.bytecode.*;
import javassist.bytecode.annotation.*;

import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.log4j.PropertyConfigurator;

import com.emc.storageos.db.TestDBClientUtils;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.*;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.upgrade.MigrationCallback;
import com.emc.storageos.db.server.upgrade.DbMigrationTestBase;
import com.emc.storageos.db.server.upgrade.util.callbacks.VersionedCustomMigrationCallback;

/**
 * DB client tests
 */
public class DbUpgradeTest extends DbMigrationTestBase {
    private static final Logger _log = LoggerFactory.getLogger(DbUpgradeTest.class);

    private static String methodName = "getPersonality";
    private static String className = "com.emc.storageos.db.client.model.Volume";
    private static String annotationName = "com.emc.storageos.db.client.model.PrefixIndex";

    private static String columnName = "personality";
    private static byte[] originalBytecodes = null;

    @Override
    protected String getSourceVersion() {
        return "2.2";
    }

    @Override
    protected String getTargetVersion() {
        return "2.5";
    }

    @Override
    protected void prepareData() throws Exception {
        if (originalBytecodes == null) {
            ClassPool pool = ClassPool.getDefault();
            CtClass cc = pool.getCtClass(className);
            //Suppress Sonar violation of Lazy initialization of static fields should be synchronized
            //Junit test will be called in single thread by default, it's safe to ignore this violation
            originalBytecodes = cc.toBytecode(); //NOSONAR ("squid:S2444")
        }

        hs.reload(className, originalBytecodes);

        // prepare data for migration
        List<URI> ids = _dbClient.queryByType(Volume.class, true);
        if (ids.iterator().hasNext()) {
            return;
        }
        
        Volume volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));
        volume.setCapacity(3456L);
        volume.setProvisionedCapacity(3456L);
        volume.setAllocatedCapacity(3456L);
        volume.setLabel("test label");
        volume.setPersonality("foo123");

        _dbClient.createObject(volume);

        //make sure the volume is saved
        ids = _dbClient.queryByType(Volume.class, true);
        List<URI> uris = new ArrayList<>();
        Iterator<URI> iterator = ids.iterator();
        if (iterator.hasNext()) {
            uris.add(iterator.next());
        }
        Assert.assertEquals(1, TestDBClientUtils.size(uris));

        volume = _dbClient.queryObject(Volume.class, uris.get(0));
        Assert.assertNotNull(volume);
    }

    @Override
    protected void changeSchema() throws Exception {
        Map<String, Object> values = new HashMap();
        values.put("cf", "foo");
        addAnnotation(className, methodName, annotationName, values);

        verifyAnnotation();
    }

    private void verifyAnnotation() throws Exception {
        Class clazz = Class.forName(className);
        Method method = clazz.getDeclaredMethod(methodName);  

        //getting the annotation
        Class annotationClazz = Class.forName(annotationName);
        java.lang.annotation.Annotation annotation = method.getAnnotation(annotationClazz);
        Assert.assertNotNull(annotation);
    }

    @Override
    public void verifyResults() throws Exception {
        Class clazz = Class.forName(className);
        List<URI> ids = 
          _dbClient.queryByConstraint(PrefixConstraint.Factory.getConstraint(clazz, columnName, "foo"));
        Assert.assertEquals(1, ids.size());
    }
}

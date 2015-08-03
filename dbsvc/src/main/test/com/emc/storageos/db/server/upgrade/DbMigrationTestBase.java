/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade;

import java.util.*;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;
import javassist.*;
import javassist.util.*;
import javassist.bytecode.*;
import javassist.bytecode.annotation.*;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The DB migration test base class
 */
public abstract class DbMigrationTestBase extends DbSimpleMigrationTestBase {
    private static final Logger _log = LoggerFactory.getLogger(DbMigrationTestBase.class);
    protected static volatile HotSwapper hs = null;

    /**
     * @return the DB version upgraded from
     */
    protected abstract String getSourceVersion();

    /**
     * @return the DB version upgraded to
     */
    protected abstract String getTargetVersion();

    /**
     * Implement this method to create test data to be migrated
     * 
     * @throws Exception
     */
    protected abstract void prepareData() throws Exception;

    /**
     * Implement this method to verify that your test data was properly migrated
     * 
     * @throws Exception
     */
    protected abstract void verifyResults() throws Exception;

    /**
     * Sub-classes can implement this method if they wish to make modifications
     * to the DB schema after "before migration" database setup has been completed
     * and prepareData() has executed, but prior to running the migration.
     * 
     * The default implementation does nothing.
     * 
     * @throws Exception
     */
    protected void changeSchema() throws Exception {
    }

    @Test
    @Override
    public void runTest() throws Exception {
        setupDB();
        prepareData();
        stopAll();
        changeSchema();
        runMigration();
        verifyResults();
    }

    protected void setupDB() throws Exception {
        if (hs == null) {
            // Suppress Sonar violation of Lazy initialization of static fields should be synchronized
            // Junit test will be called in single thread by default, it's safe to ignore this violation
            hs = new HotSwapper(8000); // NOSONAR ("squid:S2444")
        }
        super.setupDB();
    }

    // Note: the className and annotationName should be full package names
    protected static void addAnnotation(String className, String methodName,
            String annotationName, Map<String, Object> values) throws Exception {
        // pool creation
        ClassPool pool = ClassPool.getDefault();

        // extracting the class
        CtClass cc = pool.getCtClass(className);
        cc.defrost();

        // looking for the method to apply the annotation on
        CtMethod methodDescriptor = cc.getDeclaredMethod(methodName);

        // create the annotation
        ClassFile ccFile = cc.getClassFile();
        ccFile.setVersionToJava5();

        ConstPool constpool = ccFile.getConstPool();

        MethodInfo minfo = methodDescriptor.getMethodInfo();

        AnnotationsAttribute attr = (AnnotationsAttribute) minfo.getAttribute(AnnotationsAttribute.visibleTag);
        Annotation annot = new Annotation(annotationName, constpool);

        Set<Map.Entry<String, Object>> entries = values.entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            String attrName = entry.getKey();
            Object attrValue = entry.getValue();

            if (attrValue instanceof String) {
                annot.addMemberValue(attrName, new StringMemberValue((String) attrValue, ccFile.getConstPool()));
            } else {
                throw new RuntimeException(String.format("Unsupported attribute type %s of %s", attrName, attrValue));
            }
        }

        attr.addAnnotation(annot);

        // add the annotation to the method descriptor
        minfo.addAttribute(attr);

        _log.info("add {} to method {}", attr, methodDescriptor);

        // transform the ctClass to java class
        // so we can get the new added annotations through Class object.
        byte[] byteCodes = cc.toBytecode();
        hs.reload(className, byteCodes);
    }

    protected static void removeAnnotation(String className, String methodName, String annotationName) throws Exception {
        // pool creation
        ClassPool pool = ClassPool.getDefault();

        // extracting the class
        CtClass cc = pool.getCtClass(className);
        cc.defrost();

        // looking for the method to apply the annotation on
        CtMethod methodDescriptor = cc.getDeclaredMethod(methodName);

        // create the annotation
        ClassFile ccFile = cc.getClassFile();
        ccFile.setVersionToJava5();

        ConstPool constpool = ccFile.getConstPool();

        MethodInfo minfo = methodDescriptor.getMethodInfo();

        AnnotationsAttribute attr = (AnnotationsAttribute) minfo.getAttribute(AnnotationsAttribute.visibleTag);
        Annotation[] annotations = attr.getAnnotations();
        List<Annotation> list = new ArrayList();

        for (Annotation annotation : annotations) {
            if (!annotation.getTypeName().equals(annotationName)) {
                list.add(annotation);
            }
        }

        Annotation[] newAnnotations = list.toArray(new Annotation[0]);

        attr.setAnnotations(newAnnotations);

        minfo.addAttribute(attr);

        // transform the ctClass to java class
        // so we can get the new added annotations through Class object.
        byte[] byteCodes = cc.toBytecode();
        hs.reload(className, byteCodes);
    }
}

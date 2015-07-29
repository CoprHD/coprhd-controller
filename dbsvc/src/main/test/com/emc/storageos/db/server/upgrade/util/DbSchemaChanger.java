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

package com.emc.storageos.db.server.upgrade.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.StringMemberValue;
import javassist.util.HotSwapper;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.LoggingUtils;

/**
 * Change the class schema at runtime
 */
public class DbSchemaChanger {
    static {
        LoggingUtils.configureIfNecessary("dbtest-log4j.properties");
    }
    private static Logger log = LoggerFactory.getLogger(DbSchemaChanger.class);

    private String className;
    private ClassPool pool;
    private CtClass cc;

    private File classFile; // full path name
    private File backupFile;

    /**
     * Create a DB schema changer
     * 
     * @param className the name of the class whose schema is to be changed
     *            it should be the full class name
     */
    public DbSchemaChanger(String className) throws Exception {
        this.className = className;

        // pool creation
        pool = ClassPool.getDefault();

        // extracting the class
        cc = pool.getCtClass(className);

        String classFileName = cc.getURL().getFile();
        classFile = new File(classFileName);

        log.info("The class file:{}  package:{}", classFileName, cc.getPackageName());
    }

    /**
     * Begin change the schema
     * This method should be called before making any changes to the class
     */
    public DbSchemaChanger beginChange() throws Exception {
        // backup the original class file
        backupFile = File.createTempFile("dataobj", ".class");

        log.info("copy from {} to {}", classFile.getAbsolutePath(), backupFile.getAbsolutePath());

        copyFile(classFile, backupFile);

        cc.defrost();

        return this;
    }

    /**
     * After make the changes to the class, this method should be called to make the changes
     * taking effect
     */
    public void endChange() throws Exception {
        String dir = getClassRootDir();
        log.info("write changed class back to {}", dir);
        cc.writeFile(dir);
        cc.detach();

        log.info("wait 5 seconds for changes to take effect");
        // sleep 5 seconds to wait for the schema changes taking effect.
        Thread.currentThread().sleep(5000);
        log.info("done");
    }

    /*
     * return the root dir of a class file
     * i.e. return the ${root} of {root}/pkg1/pkg2/foo.class
     */
    private String getClassRootDir() {
        // translate package into sub dirs
        String subDirs = cc.getPackageName().replace('.', '/');

        String classFileName = classFile.getAbsolutePath();
        int lastIndex = classFileName.lastIndexOf(subDirs);

        return classFileName.substring(0, lastIndex);
    }

    /**
     * restore the class to the version before the change
     */
    public void restoreClass() throws Exception {
        if (backupFile == null) {
            return;
        }

        copyFile(backupFile, classFile);

        log.info("wait 5 seconds for restored class to take effect");

        // wait 5 seconds for the changes to take effect.
        Thread.currentThread().sleep(5000);
        log.info("restore done");

        // delete backup file
        boolean deleted = backupFile.delete();
        log.info("delete backup file {} sucess={}", backupFile.getAbsolutePath(), deleted);
        backupFile = null;
    }

    /**
     * add an annotation to a method
     * 
     * @param methodName the method to which the annotation to be added
     * @param annotationName the annotation name, it should be a full name
     * @param values the attributes of the annotation
     */
    public DbSchemaChanger addAnnotation(String methodName, String annotationName, Map<String, Object> values)
            throws Exception {
        // looking for the method to apply the annotation on
        CtMethod methodDescriptor = cc.getDeclaredMethod(methodName);

        // create the annotation
        ClassFile ccFile = cc.getClassFile();
        ccFile.setVersionToJava5();

        ConstPool constpool = ccFile.getConstPool();

        MethodInfo minfo = methodDescriptor.getMethodInfo();

        AnnotationsAttribute attr = (AnnotationsAttribute) minfo.getAttribute(AnnotationsAttribute.visibleTag);
        if (attr == null) {
            attr = new AnnotationsAttribute(constpool, AnnotationsAttribute.visibleTag);
        }

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

        log.info("add {} to method {}", attr, methodDescriptor);

        return this;
    }

    /**
     * remove an annotation from a method
     * 
     * @param methodName the method to which the annotation to be removed
     * @param annotationName the annotation name, it should be a full name
     */
    public DbSchemaChanger removeAnnotation(String methodName, String annotationName) throws Exception {
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

        return this;
    }

    /**
     * Add a bean property to the class i.e. add followings to a class:
     * 1. a class field
     * 2. a getter method
     * 3. a setter method
     * 
     * @param propertyName the bean property name
     * @param propertyClazz the bean property type
     * @param columnName the corresponding column name of the bean property
     */
    public <T> DbSchemaChanger addBeanProperty(String propertyName, Class<T> propertyClazz, String columnName)
            throws Exception {
        CtField f = new CtField(pool.get(propertyClazz.getName()), propertyName, cc);
        cc.addField(f);

        String getterMethodName = generateGetterMethodName(propertyName);

        StringBuilder method = new StringBuilder("public ");
        method.append(propertyClazz.getName()); // return type
        method.append(" ");
        method.append(getterMethodName);
        method.append("() {\n    return ");
        method.append(propertyName);
        method.append(";\n    }");

        log.info("Generate getter method = {}", method.toString());
        CtMethod getter = CtNewMethod.make(method.toString(), cc);
        cc.addMethod(getter);

        String setterMethodName = generateSetterMethodName(propertyName);

        method = new StringBuilder("public void ");
        method.append(setterMethodName);
        method.append("(");
        method.append(propertyClazz.getName());
        method.append(" ");
        method.append(propertyName);
        method.append(") {\n    this.");
        method.append(propertyName);
        method.append(" = ");
        method.append(propertyName);
        method.append(";\n    setChanged(\"");
        method.append(columnName);
        method.append("\");\n    }");

        log.info("Generate setter method = {}", method.toString());

        CtMethod setter = CtNewMethod.make(method.toString(), cc);
        cc.addMethod(setter);

        dumpClassInfo();

        return this;
    }

    private static String generateGetterMethodName(String propertyName) {
        StringBuilder builder = new StringBuilder("get");

        return buildMethodName(builder, propertyName);
    }

    private static String buildMethodName(StringBuilder builder, String propertyName) {
        char firstChar = propertyName.charAt(0);
        int index = 1;

        if (firstChar == '_') {
            firstChar = propertyName.charAt(1);
            index = 2;
        }

        builder.append(String.valueOf(firstChar).toUpperCase());
        builder.append(propertyName.substring(index));

        return builder.toString();
    }

    private static String generateSetterMethodName(String propertyName) {
        StringBuilder builder = new StringBuilder("set");

        return buildMethodName(builder, propertyName);
    }

    private void dumpClassInfo() {
        CtField[] fields = cc.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            log.info(fields[i].getName());
        }

        CtMethod[] methods = cc.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            log.info(methods[i].getName());
        }
    }

    /**
     * Remove a bean property from the class
     * 
     * @param propertyName the name of the property to be remooved
     */
    public DbSchemaChanger removeBeanProperty(String propertyName) throws Exception {
        String getterMethodName = generateGetterMethodName(propertyName);
        removeMethod(getterMethodName);

        String setterMethodName = generateSetterMethodName(propertyName);
        removeMethod(setterMethodName);

        dumpClassInfo();

        return this;
    }

    /**
     * remove the method from the class
     * 
     * @param methodName the name of the method to be removed
     */
    private void removeMethod(String methodName) throws Exception {
        CtMethod[] methods = cc.getDeclaredMethods();
        CtMethod method = null;

        for (CtMethod m : methods) {
            if (m.getName().equals(methodName)) {
                method = m;
                break;
            }
        }

        if (method != null) {
            cc.removeMethod(method);
            log.info("The method {} is removed", methodName);
        } else {
            log.warn("The class {} has not method {} ", cc.getName(), methodName);
        }
    }

    /**
     * verify the method does have the annotation
     * 
     * @param methodName the name of the method to be checked
     * @param annotationName the name of the annotation to be checked
     */
    public void verifyAnnotation(String methodName, String annotationName) throws Exception {
        Class clazz = Class.forName(className);
        Method method = clazz.getDeclaredMethod(methodName);

        // getting the annotation
        Class annotationClazz = Class.forName(annotationName);
        java.lang.annotation.Annotation annotation = method.getAnnotation(annotationClazz);
        Assert.assertNotNull(annotation);
    }

    /**
     * verify the class doesn't have the bean property
     * 
     * @param propertyName the name of the property
     */
    public void verifyBeanPropertyNotExist(String propertyName) throws Exception {
        Class clazz = Class.forName(className);

        // make sure that the 'getter' method doesn't exist
        String getterMethodName = generateGetterMethodName(propertyName);
        verifyMethodNotExist(getterMethodName);

        String setterMethodName = generateSetterMethodName(propertyName);
        verifyMethodNotExist(setterMethodName);
    }

    private void verifyMethodNotExist(String methodName) throws Exception {
        Method method = getMethod(methodName);

        Assert.assertNull(method);
    }

    private Method getMethod(String methodName) throws Exception {
        Class clazz = Class.forName(className);
        Method method = null;
        Method[] methods = clazz.getMethods();
        for (Method m : methods) {
            if (m.getName().equals(methodName)) {
                method = m;
                break;
            }
        }

        return method;
    }

    /**
     * verify the class has the bean property
     * 
     * @param propertyName the name of the property
     */
    public void verifyBeanPropertyExist(String propertyName) throws Exception {
        Class clazz = Class.forName(className);

        // make sure that the 'getter' method doesn't exist
        String getterMethodName = generateGetterMethodName(propertyName);
        verifyMethodExist(getterMethodName);

        String setterMethodName = generateSetterMethodName(propertyName);
        verifyMethodExist(setterMethodName);
    }

    private void verifyMethodExist(String methodName) throws Exception {
        Method method = getMethod(methodName);
        Assert.assertNotNull(method);
    }

    private void copyFile(File sourceFile, File targetFile) throws Exception {
        log.info("copy from {} to {}", sourceFile, targetFile);
        FileChannel source = new FileInputStream(sourceFile).getChannel();
        FileChannel target = new FileOutputStream(targetFile).getChannel();
        target.transferFrom(source, 0, source.size());
    }

    public enum InjectModeEnum {
        BEFORE,
        AFTER;
    }

    public void insertCodes(String methodName, String codes, InjectModeEnum mode) throws Exception {
        CtMethod method = cc.getDeclaredMethod(methodName);
        doInsertCodes(method, codes, mode);

        byte[] classFile = cc.toBytecode();
        HotSwapper hs = new HotSwapper(8000);  // 8000 is a port number.
        hs.reload(className, classFile);
    }

    private static void doInsertCodes(CtMethod method, String codes, InjectModeEnum mode) throws Exception {
        switch (mode) {
            case BEFORE:
                method.insertBefore(codes);
                break;
            case AFTER:
                method.insertAfter(codes);
                break;
            default:
                log.error("The inject mode({}) is not supported.", mode.toString());
        }
    }

    /**
     * Check if the class has been loaded by the system class loader or not
     */
    public boolean isLoaded() {
        try {
            // ClassLoader.findLoadedClass() is 'protected'
            // make it accessible outside
            Method m = ClassLoader.class.getDeclaredMethod("findLoadedClass", new Class[] { String.class });
            m.setAccessible(true);

            // call cl.findLoadedClass(className)
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            Object obj = m.invoke(cl, className);
            return obj != null;
        } catch (Exception e) {
            log.error("Failed to check if the class {} is loaded e=", className, e);
        }

        return false;
    }
}

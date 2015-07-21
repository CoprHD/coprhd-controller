/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.processing;

import com.sun.javadoc.Type;

/**
 */
public class TypeUtils {

    public static boolean isPrimitiveType(Type type) {
        return type.isPrimitive() ||
               type.qualifiedTypeName().startsWith("java.") ||
               !type.qualifiedTypeName().startsWith("com.emc");
    }

    public static boolean isCollectionType(Type type) {
        return (type.qualifiedTypeName().equals("java.util.List") ||
                type.qualifiedTypeName().equals("java.util.Set") ||
                type.qualifiedTypeName().equals("java.util.Collection"));
    }
}

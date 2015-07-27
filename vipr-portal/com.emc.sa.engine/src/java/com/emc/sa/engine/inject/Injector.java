/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.inject;

import java.lang.reflect.Field;
import java.util.Map;

import javax.inject.Inject;

public class Injector {
    public static void inject(Object target, Map<Class<?>, Object> data) {
        Class<?> superClass = target.getClass();
        while (!superClass.equals(Object.class)) {
            Field[] fields = superClass.getDeclaredFields();
            for (Field field : fields) {
                injectField(target, field, data);
            }
            superClass = superClass.getSuperclass();
        }
    }

    private static void injectField(Object target, Field field, Map<Class<?>, Object> data) {
        String fieldName = field.getName();
        Class<?> fieldType = field.getType();
        Inject inject = field.getAnnotation(Inject.class);
        if (inject == null) {
            return;
        }

        Object value = data.get(fieldType);
        if ((value != null) && fieldType.isInstance(value)) {
            try {
                field.setAccessible(true);
                field.set(target, value);
            }
            catch (Exception e) {
                String message = String.format("Error injecting field '%s'", fieldName);
                throw new InjectorException(message, e);
            }
        }
    }
}

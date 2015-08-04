/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.bind;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to indicate that a parameter should be bound before execution.
 * 
 * @author Chris Dail
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Param {
    /** Name of the parameter to bind, if empty it will use the field name. */
    String value() default "";

    /** Indicates if the parameter is required. */
    boolean required() default true;
}

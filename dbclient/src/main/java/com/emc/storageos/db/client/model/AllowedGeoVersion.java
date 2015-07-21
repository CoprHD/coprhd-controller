/*
 * Copyright (c) 2011-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * You should mark this annotation for any newly added fields on existing CF or new CF in geodb,   
 * Modification to the new field/CF is not allowed until all VDCs reach the same GeoVersion or beyond.
 */

@Documented
@Target({ElementType.METHOD,ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedGeoVersion {
	String version() default "";
}

/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to tag the cf without inactive field
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed
public @interface NoInactiveIndex {
}

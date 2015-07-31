/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for excluding classes from GarbageCollection
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed
public @interface ExcludeFromGarbageCollection {
}

/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.lang.annotation.*;

/**
 * When annotated on a type derived from AbstractChangeTrackingMap or AbstractChangeTrackingSet
 * URI strings from key values of the map are used for Relational indexing
 */
@Documented
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed()
public @interface IndexByKey {

}

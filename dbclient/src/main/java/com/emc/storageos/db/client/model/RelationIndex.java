/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.upgrade.AddIndexMigrationCallback;

import java.lang.annotation.*;

/**
 * When marked with this annotation, an inverted index is updated whenever
 * URI field is updated. This inverted index contains
 * 
 * field value -> DataObject.id relation
 * 
 * and can be queried by using Constraint queries
 */
@Documented
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed(migrationCallback = AddIndexMigrationCallback.class)
public @interface RelationIndex {
    // column family name for the index
    String cf() default "";

    // type of the referenced object
    Class<? extends DataObject> type();
    /*
     * types of the referenced objects if has multiple dependencies, 
     * it would be ignored if type set to concrete model class
     */
    Class<? extends DataObject>[] types() default {};

    // deactivate object when last referenced object is removed
    boolean deactivateIfEmpty() default false;
}

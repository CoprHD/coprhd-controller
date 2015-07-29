/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.upgrade.AddIndexMigrationCallback;

import java.lang.annotation.*;

/**
 * When marked with this annotation, an inverted index is updated whenever
 * NamedURI field is updated. This inverted index contains
 * 
 * field value + name -> DataObject.id relation
 * 
 * and can be queried by using Constraint queries.
 */
@Documented
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed(migrationCallback = AddIndexMigrationCallback.class)
public @interface NamedRelationIndex {
    // column family name for the index
    String cf() default "";

    // type of the referenced object - optional, for grandparent relations
    Class<? extends DataObject> type() default DataObject.class;
}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
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
import com.emc.storageos.db.client.impl.ColumnField;

import java.lang.annotation.*;

/**
 * This annotation should be used to prepare an Indexed CF that can be used for aggregation.
 *
 * The values in get/set methods annotated here are duplicated with an Indexed CF
 * based on the grouping by "groupBy" value
 *
 * ClassName:goupByValue -> filed = "property":"recordId" ; value = "propertyValue"
 *
 * If "groupBy" value is not specified, the aggreated record can be created for the class:
 *
 * ClassName -> filed = "property":"recordId" ; value = "propertyValue"
 *
 * Aggregation can be queried by using the corresponding Constraint queries
 *
 * Known limitations:
 * Annotation should be applied only to properties of Primitive type. (No Map, Sets, SetMap, NestedObjects)
 * GroupBy fields must be also primitive.
 */
@Documented
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed(migrationCallback = AddIndexMigrationCallback.class)
public @interface AggregatedIndex {
    // column family name for the index
    String cf() default "";

    // group by feilds
    String groupBy() default "";

    boolean classGlobal() default false;
}

/**
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
 * When annotated on a type derived from AbstractChangeTrackingMap or AbstractChangeTrackingSet
 * URI strings from key values of the map are used for Relational indexing
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed()
public @interface IndexByKey {

}

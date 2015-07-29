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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.emc.storageos.db.client.upgrade.callbacks.GeoDbMigrationCallback;

/**
 * Annotation to identify Geo-Replicated data objects
 * 
 * @author cgarber
 * 
 */
@Documented
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed(migrationCallback = GeoDbMigrationCallback.class)
public @interface DbKeyspace {
    public enum Keyspaces {
        LOCAL, GLOBAL
    }

    Keyspaces value();
}

/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
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
 * @author cgarber
 *
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed(migrationCallback=GeoDbMigrationCallback.class)
public @interface DbKeyspace {
    public enum Keyspaces {
        LOCAL, GLOBAL
    }
    Keyspaces value();
}

/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;

import com.emc.storageos.model.block.MigrationCreateParam;
import com.emc.storageos.model.block.MigrationEnvironmentParam;

/**
 * Interface for block migration service calls.
 */
public interface MigrationServiceApi {

    /**
     * Define the default BlockServiceApi implementation.
     */
    public static final String DEFAULT = "default";

    public static final String CONTROLLER_SVC = "controllersvc";
    public static final String CONTROLLER_SVC_VER = "1";
    public static final String EVENT_SERVICE_TYPE = "block";

    /**
     * Create migration environment between source system and target system
     */
    public void migrationCreateEnvironment(MigrationEnvironmentParam param, String task);

    /**
     * Create/Initiate the migration process
     * 
     */
    public void migrationCreate(URI cgId, MigrationCreateParam param, String task);

    /**
     * Cutover the migration process
     * 
     */
    public void migrationCutover(URI cgId, String task);

    /**
     * Commit the migration process
     */
    public void migrationCommit(URI cgId, String task);

    /**
     * Cancel the migration process
     * 
     */
    public void migrationCancel(URI cgId, String task);

    /**
     * Update the status of the migration job
     */
    public void migrationRefresh(URI cgId, String task);

    /**
     * Recover the migration process
     * 
     */
    public void migrationRecover(URI cgId, String task);

    /**
     * Stop the data synchronization of source volumes from target volumes
     */
    public void migrationSyncStop(URI cgId, String task);

    /**
     * Start the data synchronization of source volumes from target volumes
     */
    public void migrationSyncStart(URI cgId, String task);

    /**
     * Remove the migration environment between source system and target system
     */
    public void migrationRemoveEnvironment(MigrationEnvironmentParam param, String task);
}

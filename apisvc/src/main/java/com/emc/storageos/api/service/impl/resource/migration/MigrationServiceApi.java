/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.migration;

import java.net.URI;

import com.emc.storageos.model.block.MigrationCreateParam;
import com.emc.storageos.model.block.MigrationEnvironmentParam;

/**
 * Interface for block migration service calls.
 */
public interface MigrationServiceApi {

    public static final String CONTROLLER_SVC = "controllersvc";
    public static final String CONTROLLER_SVC_VER = "1";

    /**
     * Create migration environment between source system and target system
     */
    public void migrationCreateEnvironment(MigrationEnvironmentParam param, String taskId);

    /**
     * Create/Initiate the migration process
     * 
     */
    public void migrationCreate(URI cgId, MigrationCreateParam param, String taskId);

    /**
     * Cutover the migration process
     * 
     */
    public void migrationCutover(URI cgId, String taskId);

    /**
     * Commit the migration process
     */
    public void migrationCommit(URI cgId, String taskId);

    /**
     * Cancel the migration process
     * 
     */
    public void migrationCancel(URI cgId, String taskId);

    /**
     * Update the status of the migration job
     */
    public void migrationRefresh(URI cgId, String taskId);

    /**
     * Recover the migration process
     * 
     */
    public void migrationRecover(URI cgId, String taskId);

    /**
     * Stop the data synchronization of source volumes from target volumes
     */
    public void migrationSyncStop(URI cgId, String taskId);

    /**
     * Start the data synchronization of source volumes from target volumes
     */
    public void migrationSyncStart(URI cgId, String taskId);

    /**
     * Remove the migration environment between source system and target system
     */
    public void migrationRemoveEnvironment(MigrationEnvironmentParam param, String taskId);
}

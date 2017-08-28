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
     */
    public void migrationCreate(URI cgURI, URI migrationURI, MigrationCreateParam param, String taskId);

    /**
     * Cutover the migration process
     */
    public void migrationCutover(URI cgURI, URI migrationURI, String taskId);

    /**
     * Commit the migration process
     */
    public void migrationCommit(URI cgURI, URI migrationURI, String taskId);

    /**
     * Cancel the migration process
     */
    public void migrationCancel(URI cgURI, URI migrationURI, boolean cancelWithRevert, String taskId);

    /**
     * Update the status of the migration job
     */
    public void migrationRefresh(URI cgURI, URI migrationURI, String taskId);

    /**
     * Recover the migration process
     */
    public void migrationRecover(URI cgURI, URI migrationURI, boolean force, String taskId);

    /**
     * Stop the data synchronization of source volumes from target volumes
     */
    public void migrationSyncStop(URI cgURI, URI migrationURI, String taskId);

    /**
     * Start the data synchronization of source volumes from target volumes
     */
    public void migrationSyncStart(URI cgURI, URI migrationURI, String taskId);

    /**
     * Remove the migration environment between source system and target system
     */
    public void migrationRemoveEnvironment(MigrationEnvironmentParam param, String taskId);
}

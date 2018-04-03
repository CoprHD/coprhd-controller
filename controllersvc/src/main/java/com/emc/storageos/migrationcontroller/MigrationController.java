/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */

package com.emc.storageos.migrationcontroller;

import java.net.URI;

import com.emc.storageos.Controller;
import com.emc.storageos.volumecontroller.ControllerException;

/**
 * An interface for managing the block migration operations.
 */
public interface MigrationController extends Controller {

    public final static String MIGRATION = "migration";

    /**
     * Create migration environment between source system and target system
     *
     * @param sourceSystemURI the source system uri
     * @param targetSystemURI the target system uri
     * @param taskId the task id
     * @throws ControllerException the controller exception
     */
    public void migrationCreateEnvironment(URI sourceSystemURI, URI targetSystemURI, String taskId) throws ControllerException;

    /**
     * Create/Initiate the migration process.
     *
     * @param sourceSystemURI the source system uri
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param targetSystemURI the target system uri
     * @param srp the srp
     * @param enableCompression enable compression
     * @param taskId the task id
     * @throws ControllerException the controller exception
     */
    public void migrationCreate(URI sourceSystemURI, URI cgURI, URI migrationURI, URI targetSystemURI,
            URI srp, Boolean enableCompression, String taskId) throws ControllerException;

    /**
     * Cutover the migration process
     *
     * @param sourceSystemURI the source system uri
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param taskId the task id
     * @throws ControllerException the controller exception
     */
    public void migrationCutover(URI sourceSystemURI, URI cgURI, URI migrationURI, String taskId) throws ControllerException;


    /**
     * Ready Target the migration process
     *
     * @param sourceSystemURI the source system uri
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param taskId the task id
     * @throws ControllerException the controller exception
     */
    public void migrationReadyTgt(URI sourceSystemURI, URI cgURI, URI migrationURI, String taskId) throws ControllerException;

    /**
     * Commit the migration process
     *
     * @param sourceSystemURI the source system uri
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param taskId the task id
     * @throws ControllerException the controller exception
     */
    public void migrationCommit(URI sourceSystemURI, URI cgURI, URI migrationURI, String taskId) throws ControllerException;

    /**
     * Cancel the migration process
     *
     * @param sourceSystemURI the source system uri
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param cancelWithRevert
     * @param taskId the task id
     * @throws ControllerException the controller exception
     */
    public void migrationCancel(URI sourceSystemURI, URI cgURI, URI migrationURI, boolean cancelWithRevert, String taskId)
            throws ControllerException;

    /**
     * Update the status of the migration job
     *
     * @param sourceSystemURI the source system uri
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param taskId the task id
     * @throws ControllerException the controller exception
     */
    public void migrationRefresh(URI sourceSystemURI, URI cgURI, URI migrationURI, String taskId) throws ControllerException;

    /**
     * Recover the migration process
     *
     * @param sourceSystemURI the source system uri
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param taskId the task id
     * @param force force boolean
     * @throws ControllerException the controller exception
     */
    public void migrationRecover(URI sourceSystemURI, URI cgURI, URI migrationURI, boolean force, String taskId) throws ControllerException;

    /**
     * Stop the data synchronization of source volumes from target volumes
     *
     * @param sourceSystemURI the source system uri
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param taskId the task id
     * @throws ControllerException the controller exception
     */
    public void migrationSyncStop(URI sourceSystemURI, URI cgURI, URI migrationURI, String taskId) throws ControllerException;

    /**
     * Start the data synchronization of source volumes from target volumes
     *
     * @param sourceSystemURI the source system uri
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param taskId the task id
     * @throws ControllerException the controller exception
     */
    public void migrationSyncStart(URI sourceSystemURI, URI cgURI, URI migrationURI, String taskId) throws ControllerException;

    /**
     * Remove the migration environment between source system and target system
     *
     * @param sourceSystemURI the source system uri
     * @param targetSystemURI the target system uri
     * @param taskId the task id
     * @throws ControllerException the controller exception
     */
    public void migrationRemoveEnvironment(URI sourceSystemURI, URI targetSystemURI, String taskId) throws ControllerException;
}
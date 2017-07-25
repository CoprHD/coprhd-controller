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

    public final static String MIGRATION = "migration"; // TODO tag needed?

    /**
     * Create Migration environment.
     *
     * @param sourceSystem the source system
     * @param targetSystem the target system
     * @param taskId the task id
     * @throws ControllerException the controller exception
     */
    public void migrationCreateEnvironment(URI sourceSystem, URI targetSystem, String taskId) throws ControllerException;

    public void migrationCreate(URI sourceSystem, URI cgId, URI targetSystem, String taskId) throws ControllerException;

    public void migrationCutover(URI sourceSystem, URI cgId, String taskId) throws ControllerException;

    public void migrationCommit(URI sourceSystem, URI cgId, String taskId) throws ControllerException;

    public void migrationCancel(URI sourceSystem, URI cgId, String taskId) throws ControllerException;

    public void migrationRefresh(URI sourceSystem, URI cgId, String taskId) throws ControllerException;

    public void migrationRecover(URI sourceSystem, URI cgId, String taskId) throws ControllerException;

    public void migrationSyncStop(URI sourceSystem, URI cgId, String taskId) throws ControllerException;

    public void migrationSyncStart(URI sourceSystem, URI cgId, String taskId) throws ControllerException;

    public void migrationRemoveEnvironment(URI sourceSystem, URI targetSystem, String taskId) throws ControllerException;
}
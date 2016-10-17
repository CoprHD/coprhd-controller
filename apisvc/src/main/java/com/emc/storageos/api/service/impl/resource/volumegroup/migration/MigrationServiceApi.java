/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.volumegroup.migration;

import java.net.URI;

import com.emc.storageos.model.application.ApplicationMigrationParam;

public interface MigrationServiceApi {
	
	//TODO: comments, javadoc, etc. Also, fix method signatures as work starts. These are just bare-bone stubs.
	
	/**
	 * Create migration creates migration environment where applicable
	 */
	public void migrationCreate(URI applicationId, ApplicationMigrationParam param);
		
	/**
	 * Initiate the migration process
	 * 
	 */
	public void migrationMigrate();
		
	/**
	 * Commit the migration process
	 */
	public void migrationCommit();
		
	/**
	 * Cancel migration
	 * 
	 */
	public void migrationCancel(boolean removeEnvironment);
	
	/**
	 * Updates the status of migration jobs
	 */
	public void migrationRefresh();
	
	/**
	 * Recover migration
	 * 
	 */
	public void migrationRecover();
	
	/**
	 * Dismantle/clean-up all resources related to migration
	 */
	public void migrationRemoveEnv();
	
	/**
	 * 
	 */
	public void migrationSyncStart();
	
	/**
	 * 
	 */
	public void migrationSyncStop();
}

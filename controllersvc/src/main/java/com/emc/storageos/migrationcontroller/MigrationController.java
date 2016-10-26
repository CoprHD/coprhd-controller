/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.migrationcontroller;

import com.emc.storageos.Controller;

public interface MigrationController extends Controller {
	
    public final static String MIGRATION_ORCHESTRATION_DEVICE = "migration";
	
	public void migrationCreate();
	
	public void migrationMigrate();
	
	public void migrationCommit();
	
	public void migrationCancel();
	
	public void migrationRefresh();
	
	public void migrationRecover();
	
	public void migrationRemoveEnv();
	
	public void migrationSyncStart();
	
	public void migrationSyncStop();
}

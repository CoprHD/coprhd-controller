/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.migrationcontroller;

import java.net.URI;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.model.VolumeGroup;

public interface MigrationController extends Controller {
	
    public final static String MIGRATION_ORCHESTRATION_DEVICE = "migration";
	
    public void migrationCreate(URI volumeGroupURI) throws Exception;
	
	public void migrationMigrate();
	
	public void migrationCommit();
	
	public void migrationCancel();
	
	public void migrationRefresh();
	
	public void migrationRecover();
	
	public void migrationRemoveEnv();
	
	public void migrationSyncStart();
	
	public void migrationSyncStop();
}

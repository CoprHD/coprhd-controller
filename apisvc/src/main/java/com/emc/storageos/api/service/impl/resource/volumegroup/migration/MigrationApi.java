package com.emc.storageos.api.service.impl.resource.volumegroup.migration;

public interface MigrationApi {
	
	//TODO: comments, javadoc, etc. Also, fix method signatures as work starts. These are just bare-bone stubs.
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

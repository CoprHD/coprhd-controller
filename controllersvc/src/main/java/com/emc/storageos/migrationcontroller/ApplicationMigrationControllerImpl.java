package com.emc.storageos.migrationcontroller;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;

public class ApplicationMigrationControllerImpl implements MigrationController {

     static final Logger log = LoggerFactory.getLogger(ApplicationMigrationControllerImpl.class);

	
	 private Set<ApplicationMigrationController> migrationControllerImpl;
	 private DbClient dbClient;	 

	public DbClient getDbClient() {
		return dbClient;
	}

	public void setDbClient(DbClient dbClient) {
		this.dbClient = dbClient;
	}
	
	public ApplicationMigrationController getMigrationController() {
		return migrationControllerImpl.iterator().next();
	}

	public void setMigrationControllerImpl(Set<ApplicationMigrationController> migrationControllerImpl) {
		this.migrationControllerImpl = migrationControllerImpl;
	}	

	    
	@Override
	public void migrationCreate() {
		// TODO Auto-generated method stub
		log.info("ApplicationMigrationControllerImpl : Start Migration Create");
		getMigrationController().migrationCreate();		
	}

	@Override
	public void migrationMigrate() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void migrationCommit() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void migrationCancel() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void migrationRefresh() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void migrationRecover() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void migrationRemoveEnv() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void migrationSyncStart() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void migrationSyncStop() {
		// TODO Auto-generated method stub
		
	}

	
}

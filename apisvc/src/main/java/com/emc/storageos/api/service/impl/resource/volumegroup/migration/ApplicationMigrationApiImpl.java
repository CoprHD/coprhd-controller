/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.volumegroup.migration;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.migrationcontroller.MigrationController;
import com.emc.storageos.model.application.ApplicationMigrationParam;

public class ApplicationMigrationApiImpl extends AbstractMigrationServiceApiImpl {

	private static final Logger logger = LoggerFactory.getLogger(ApplicationMigrationApiImpl.class);		
    
    public ApplicationMigrationApiImpl(String migrationType) {
		super(migrationType);
		// TODO Auto-generated constructor stub
	}
    
    //Migration related methods
	@Override
	public void migrationCreate(URI applicationId, ApplicationMigrationParam param) {
		logger.info("Migration : Create");		
		getController(MigrationController.class, "application").migrationCreate();
	}

	@Override
	public void migrationMigrate() {
		logger.info("Migration : Migrate");
	}

	@Override
	public void migrationCommit() {
		logger.info("Migration : Commit");
	}

	@Override
	public void migrationCancel(boolean removeEnvironment) {
		logger.info("Migration : Cancel");
	}

	@Override
	public void migrationRefresh() {
		logger.info("Migration : Refresh");
	}

	@Override
	public void migrationRecover() {
		logger.info("Migration : Recover");
	}

	@Override
	public void migrationRemoveEnv() {
		logger.info("Migration : Remove Environment");
	}

	@Override
	public void migrationSyncStart() {
		logger.info("Migration : Sync Start");
	}

	@Override
	public void migrationSyncStop() {
		logger.info("Migration : Sync Stop");
	}
}

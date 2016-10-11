package com.emc.storageos.api.service.impl.resource.volumegroup.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplicationMigrationApiImpl implements MigrationApi {

	
    private static final Logger logger = LoggerFactory.getLogger(ApplicationMigrationApiImpl.class);

	@Override
	public void migrationCreate() {
		logger.info("Migration : Create");		
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
	public void migrationCancel() {
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

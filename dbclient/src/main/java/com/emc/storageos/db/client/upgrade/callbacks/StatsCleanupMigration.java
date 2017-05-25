package com.emc.storageos.db.client.upgrade.callbacks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.TimeSeriesType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StatTimeSeries;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import com.netflix.astyanax.model.ConsistencyLevel;

/**
 * This migration handler is for COP-30611. It will do two things. 
 * 
 * 1. Truncate stats CF with DB consistency level as ALL (suggested by datastax)
 * 2. Change compaction strategy to SizeTieredCompactionStrategy.
 *
 */
public class StatsCleanupMigration extends BaseCustomMigrationCallback {
	private static final Logger log = LoggerFactory.getLogger(StatsCleanupMigration.class);
	
	@Override
	public void process() throws MigrationCallbackException {
		log.info("begin to cleanup stats CF and change it compaction strategy");
		
		DbClientImpl dbClient = (DbClientImpl)getDbClient();
		TimeSeriesType<Stat> doType = TypeMap.getTimeSeriesType(StatTimeSeries.class);
		
		try {
			dbClient.getLocalContext().getKeyspace().prepareQuery(doType.getCf())
					.setConsistencyLevel(ConsistencyLevel.CL_ALL)
					.withCql(String.format("TRUNCATE TABLE \"%s\"", doType.getCf().getName())).execute();
			dbClient.getLocalContext().getKeyspace().prepareQuery(doType.getCf())
					.withCql(String.format(
							"ALTER TABLE \"%s\" WITH compaction = {'class': 'SizeTieredCompactionStrategy'}",
							doType.getCf().getName()))
					.execute();
		} catch (Exception e) {
			log.error("Failed to cleanup stats CF {}", e);
			throw new MigrationCallbackException("Failed to cleanup stats CF", e);
		}
	}

}

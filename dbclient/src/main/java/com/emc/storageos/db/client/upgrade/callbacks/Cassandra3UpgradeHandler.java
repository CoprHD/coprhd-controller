package com.emc.storageos.db.client.upgrade.callbacks;

import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.TimeSeriesType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StatTimeSeries;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import com.netflix.astyanax.model.ConsistencyLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This migration handler is for Cassandra 3 upgrading.
 *
 * It changes replication strategy of some system keyspaces to NetworkTopologyStrategy to support DR.
 * The system keyspaces include system_distributed, system_auth and system_traces.
 *
 * Detailed explanation can be found at:
 *   http://docs.datastax.com/en/cassandra/3.0/cassandra/operations/opsAddDCToCluster.html
 *
 */
public class Cassandra3UpgradeHandler extends BaseCustomMigrationCallback {
	private static final Logger log = LoggerFactory.getLogger(Cassandra3UpgradeHandler.class);
	
	@Override
	public void process() throws MigrationCallbackException {
		log.info("begin Cassandra 3 upgrading");
		
		DbClientImpl dbClient = (DbClientImpl)getDbClient();


		DbClientContext dbContext = dbClient.getLocalContext();

		DrUtil drUtil = dbClient.getDrUtil();
		try {

			dbContext.setRepStrategyForSystemKS(drUtil);

		} catch (Exception e) {
			log.error("Exception in Cassandra 3 upgrade handler", e);
			throw new MigrationCallbackException("Exception in Cassandra 3 upgrade handler", e);
		}
	}

}

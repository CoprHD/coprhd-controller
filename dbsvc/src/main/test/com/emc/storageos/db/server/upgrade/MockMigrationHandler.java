package com.emc.storageos.db.server.upgrade;

import java.util.List;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.db.server.impl.MigrationHandlerImpl;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class MockMigrationHandler extends MigrationHandlerImpl {
	private static Logger log = LoggerFactory.getLogger(MockMigrationHandler.class);
		
	@Override
	public boolean run() throws DatabaseException {
		for(Entry<String, List<BaseCustomMigrationCallback>> entry : this.getCustomMigrationCallbacks().entrySet()) {
			log.info("execute migration callback under {}", entry.getKey());
			for(BaseCustomMigrationCallback callback : entry.getValue()) {
				callback.setName(callback.getClass().getName());
				callback.setDbClient(this.getDbClient());
				callback.setCoordinatorClient(this.getCoordinator());
				
                log.info("Invoking migration callback: " + callback.getName());
                try {
					callback.process();
				} catch (MigrationCallbackException e) {
					log.error("invoke migration callback {} failed:{}", callback.getName(), e);
					return false;
				}
			}
		}
		return true;
	}
}

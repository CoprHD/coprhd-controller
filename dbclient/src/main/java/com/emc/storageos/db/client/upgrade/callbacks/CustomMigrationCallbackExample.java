package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
/**
 * Migration callback example.
 * 
 */

public class CustomMigrationCallbackExample extends BaseCustomMigrationCallback {
	private static final int MIN_LABEL_LENGTH = 2;
	private static final String SHOW_DESCRIPTION = "invalidate label length";

	@Override
	public void process() throws MigrationCallbackException {
		this.validateHostLabelLength();
	}
	
	private void validateHostLabelLength() throws MigrationCallbackException {
		DbClient dbClient = this.getDbClient();
		List<URI> hostIds = dbClient.queryByType(Host.class, true);
		Iterator<Host> hosts = dbClient.queryIterativeObjects(Host.class, hostIds, true);
		while (hosts.hasNext()) {
			Host host = hosts.next();
			if (host.getLabel()!=null && host.getLabel().length()<MIN_LABEL_LENGTH) {
				throw new MigrationCallbackException(this.getName(), Host.class.getSimpleName(), host.getId(), SHOW_DESCRIPTION, new IllegalStateException());
			}
		}
	}

}

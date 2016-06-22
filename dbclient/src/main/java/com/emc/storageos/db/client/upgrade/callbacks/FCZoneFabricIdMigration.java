package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class FCZoneFabricIdMigration extends BaseCustomMigrationCallback {
	private static final Logger logger = LoggerFactory.getLogger(FCZoneFabricIdMigration.class);
	private List<Network> networks = null;
	
	@Override
	public void process() throws MigrationCallbackException {
		List<URI> networkIds = this.dbClient.queryByType(Network.class, false);
		this.networks = this.dbClient.queryObject(Network.class, networkIds);
		List<URI> zoneIds = this.dbClient.queryByType(FCZoneReference.class, true);
		Iterator<FCZoneReference> itr = this.dbClient.queryIterativeObjects(FCZoneReference.class, zoneIds);
		List<FCZoneReference> affectedZoneRefs = new ArrayList<FCZoneReference>();
		
		while (itr.hasNext()) {
			FCZoneReference zoneRef = itr.next();
			Network network = this.getNetwork(zoneRef);
			if (network == null) {
				logger.warn("can't find network for FCZoneReference id={}", zoneRef.getId().toString());
				continue;
			}
			zoneRef.setFabricId(network.getNativeGuid());
			affectedZoneRefs.add(zoneRef);
		}
		if (!affectedZoneRefs.isEmpty()) {
			this.dbClient.updateObject(affectedZoneRefs);
		}
	}
	
	private Network getNetwork(FCZoneReference zoneRef) {
		for (Network network : this.networks) {
			if (network.getNativeId()!=null && network.getNativeId().equals(zoneRef.getFabricId())) {
				return network;
			}
		}
		
		return null;
	}

}

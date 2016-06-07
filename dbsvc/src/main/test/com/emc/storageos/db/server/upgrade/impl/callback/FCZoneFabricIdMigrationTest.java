package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Assert;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.FCZoneFabricIdMigration;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class FCZoneFabricIdMigrationTest extends DbSimpleMigrationTestBase{
	private static final String FABRIC_ID = "MDS_46:84:4D:9E:2C:18:87:FE";
	private static final String WRONG_FABRIC_ID = "wrong_fabric_id";	

    @BeforeClass
    public static void setup() throws IOException {

        customMigrationCallbacks.put("3.5", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 1L;
            {
                // Add your implementation of migration callback below.
                add(new FCZoneFabricIdMigration());
            }
        });
    }

	@Override
	protected void prepareData() throws Exception {
		Network network = new Network();
		FCZoneReference zoneRef = new FCZoneReference();
		
		network.setId(URIUtil.createId(Network.class));
		network.setNativeGuid(FABRIC_ID);
		network.setNativeId(WRONG_FABRIC_ID);
		network.setInactive(false);
		
		zoneRef.setId(URIUtil.createId(FCZoneReference.class));
		zoneRef.setFabricId(WRONG_FABRIC_ID);
		zoneRef.setInactive(false);
		
		this._dbClient.createObject(network);
		this._dbClient.createObject(zoneRef);
	}

	@Override
	protected void verifyResults() throws Exception {
		List<URI> zoneRefIds = this._dbClient.queryByType(FCZoneReference.class, true);
		List<FCZoneReference> zoneRefs = this._dbClient.queryObject(FCZoneReference.class, zoneRefIds);
		System.out.println("fabric ref size=" + zoneRefs.size());
		for(FCZoneReference zoneRef : zoneRefs) {
			Assert.assertEquals(zoneRef.getFabricId(), FABRIC_ID);
		}
	}
}

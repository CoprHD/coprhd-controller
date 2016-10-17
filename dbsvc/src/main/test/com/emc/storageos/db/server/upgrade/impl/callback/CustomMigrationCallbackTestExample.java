package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.util.ArrayList;

import org.junit.BeforeClass;

import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.CustomMigrationCallbackExample;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class CustomMigrationCallbackTestExample extends DbSimpleMigrationTestBase {
	@SuppressWarnings("serial")
	@BeforeClass
    public static void setup() throws IOException{
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new CustomMigrationCallbackExample());
            }
        });
        DbsvcTestBase.setup();
    }
	@Override
	protected void prepareData() throws Exception {
		// prepare data in db

	}

	@Override
	protected void verifyResults() throws Exception {
		// verify result

	}

}

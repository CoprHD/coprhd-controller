/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;

import org.junit.Assert;
import org.junit.BeforeClass;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.FCZoneReferenceMigration;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class FCZoneReferenceMigrationTest extends DbSimpleMigrationTestBase {

    private URI zrId;
    private URI volId;

    @BeforeClass
    public static void setup() throws IOException {

        customMigrationCallbacks.put("2.4", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 1L;
            {
                // Add your implementation of migration callback below.
                add(new FCZoneReferenceMigration());
            }
        });

        DbSimpleMigrationTestBase.initialSetup(new AlterSchema() {
            @Override
            protected void process() {
                // No schema altering
            }
        });
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#getSourceVersion()
     */
    @Override
    protected String getSourceVersion() {
        return "2.4";
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#getTargetVersion()
     */
    @Override
    protected String getTargetVersion() {
        return "2.4.1";
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#prepareData()
     */
    @Override
    protected void prepareData() throws Exception {
        DbClient dbClient = getDbClient();

        FCZoneReference zr = new FCZoneReference();
        zrId = URIUtil.createId(FCZoneReference.class);
        zr.setId(zrId);
        volId = URIUtil.createId(Volume.class);
        zr.setLabel("1234567812345678_8765432187654321");
        zr.setVolumeUri(volId);
        dbClient.createObject(zr);        
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#verifyResults()
     */
    @Override
    protected void verifyResults() throws Exception {
        DbClient dbClient = getDbClient();
        
        FCZoneReference zr = dbClient.queryObject(FCZoneReference.class, zrId);
        Assert.assertEquals("1234567812345678_8765432187654321_" + volId, zr.getLabel());
    }

}

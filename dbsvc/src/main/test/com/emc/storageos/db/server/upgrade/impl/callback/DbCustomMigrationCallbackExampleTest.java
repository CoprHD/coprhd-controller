/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.BlockObjectConsistencyGroupMigration;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;

public class DbCustomMigrationCallbackExampleTest extends DbSimpleMigrationTestBase {
    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new DbCustomMigrationCallbackExample());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "2.4";
    }

    @Override
    protected String getTargetVersion() {
        return "2.5";
    }

    @Override
    protected void prepareData() throws Exception {
        createDataForTest()
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyResults();
    }
}

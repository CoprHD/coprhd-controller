/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.server.upgrade.DbMigrationTest;
import com.emc.storageos.db.server.upgrade.util.DbSchemaChanger;

/**
 * Prepare data for adding new fields test
 */
public class DbTestInternalFlags extends DbMigrationTest {
    private static final Logger log = LoggerFactory.getLogger(DbTestInternalFlags.class);

    @Override
    public String getSourceSchemaVersion() {
        return "2.2";
    }

    @Override
    public String getTargetSchemaVersion() {
        return "2.5";
    }

    @Override
    public void changeSourceSchema() throws Exception {
        removeInternalFlags();
    }

    private void removeInternalFlags() throws Exception {
        // Remove 'DataObject.internalFlags' which is added in version 1.1
        changer = new DbSchemaChanger("com.emc.storageos.db.client.model.DataObject");
        changer.verifyBeanPropertyExist("_internalFlags");

        changer.beginChange()
                .removeBeanProperty("_internalFlags")
                .endChange();
    }

    @Override
    protected void verifySourceSchema() throws Exception {
        DbSchemaChanger changer2 = new DbSchemaChanger("com.emc.storageos.db.client.model.DataObject");
        changer2.verifyBeanPropertyNotExist("_internalFlags");
    }

    @Override
    public void changeTargetSchema() throws Exception {
        changer.restoreClass();
    }

    @Override
    protected void verifyTargetSchema() throws Exception {
        DbSchemaChanger changer3 = new DbSchemaChanger("com.emc.storageos.db.client.model.DataObject");
        changer3.verifyBeanPropertyExist("_internalFlags");
    }

    @Override
    public void prepareData() throws Exception {
        createFileShare();
    }

    private void createFileShare() {
        // prepare FileShare objects for migration
        FileShare fs = new FileShare();

        fs.setId(URIUtil.createId(FileShare.class));
        fs.setLabel("fileshare test");

        dbClient.createObject(fs);
    }

    @Override
    protected void verifyPreparedData() throws Exception {
    }

    @Override
    protected void verifyResults() throws Exception {
    }
}

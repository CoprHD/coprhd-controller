/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade;

import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.db.client.impl.*;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.common.DataObjectScanner;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.serializers.StringSerializer;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The DB migration test base class
 */
public abstract class DbSimpleMigrationTestBase extends DbsvcTestBase {
    private static final Logger _log = LoggerFactory.getLogger(DbSimpleMigrationTestBase.class);

    /**
     * @return the DB version upgraded from
     */
    protected abstract String getSourceVersion();

    /**
     * @return the DB version upgraded to
     */
    protected abstract String getTargetVersion();

    /**
     * Implement this method to create test data to be migrated
     * 
     * @throws Exception
     */
    protected abstract void prepareData() throws Exception;

    /**
     * Implement this method to verify that your test data was properly migrated
     * 
     * @throws Exception
     */
    protected abstract void verifyResults() throws Exception;

    @Test
    public void runTest() throws Exception {
        setupDB();
        prepareData();
        stopAll();
        runMigration();
        verifyResults();
    }

    protected void setupDB() throws Exception {
        if (!isDbStarted) {
            _log.info("startDB again");
            startDb(getSourceVersion(), getSourceVersion(), null);
        }
    }

    protected void runMigration() throws Exception {
        startDb(getSourceVersion(), getTargetVersion(), null);
    }

    protected static void alterSchema() {
        _log.debug("No implementation in base class; must be overridden if schema change is needed");
    }

    public static void initialSetup(AlterSchema alterSchema) throws IOException {
        List<String> packages = new ArrayList<String>();
        packages.add("com.emc.storageos.db.client.model");

        String[] pkgsArray = packages.toArray(new String[packages.size()]);

        DataObjectScanner scanner = new DataObjectScanner();
        scanner.setPackages(pkgsArray);
        scanner.init();

        alterSchema.process();

        sourceVersion = new DbVersionInfo();
        sourceVersion.setSchemaVersion("2.2");
        _dataDir = new File("./dbtest");
        if (_dataDir.exists() && _dataDir.isDirectory()) {
            cleanDirectory(_dataDir);
        }
        startDb(sourceVersion.getSchemaVersion(), sourceVersion.getSchemaVersion(), null, scanner);

        scanner = null;

    }

    protected static abstract class AlterSchema {
        protected abstract void process();

        protected void replaceIndexCf(Class<? extends DataObject> clazz, String fieldName, String oldIndexCf) {
            ColumnFamily<String, IndexColumnName> oldIndexCF = new ColumnFamily<String, IndexColumnName>(oldIndexCf,
                    StringSerializer.get(),
                    IndexColumnNameSerializer.get());
            DataObjectType doType = TypeMap.getDoType(clazz);
            if (doType != null) {
                ColumnField field = doType.getColumnField(fieldName);
                if (field != null) {
                    DbIndex index = field.getIndex();
                    if (index != null) {
                        index.setIndexCF(oldIndexCF);
                    } else {
                        throw new IllegalArgumentException("no index on " + clazz.getSimpleName() + "." + fieldName);
                    }
                } else {
                    throw new IllegalArgumentException("no field with name: " + fieldName);
                }
            } else {
                throw new IllegalArgumentException("can't find class: " + clazz.getName() + " in class map");
            }
        }
    }
}

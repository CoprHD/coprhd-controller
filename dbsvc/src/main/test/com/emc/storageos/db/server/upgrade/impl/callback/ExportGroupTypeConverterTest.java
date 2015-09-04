/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import com.emc.storageos.db.client.impl.DbClientImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.upgrade.callbacks.ExportGroupTypeConverter;

/**
 * Test proper conversion of the type field for ExportGroup
 * with a running dbsvc.
 * 
 * It doesn't test upgrade. It tests converter with same schema as there is no schema change.
 */
public class ExportGroupTypeConverterTest {
    private DbClientImpl _dbClient = null;

    @Before
    public void setup() {
        // get DB client
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(
                "dbutils-conf.xml");
        _dbClient = (DbClientImpl) ctx.getBean("dbclient");
        _dbClient.start();
    }

    @After
    public void cleanup() {
        if (_dbClient != null) {
            _dbClient.stop();
        }
    }

    @Test
    public void testConverter() throws Exception {
        ExportGroupTypeMigrationTest typeMigrationTest = new ExportGroupTypeMigrationTest();
        typeMigrationTest.setDbClient(_dbClient);
        typeMigrationTest.prepareData();

        ExportGroupTypeConverter typeConvertor = new ExportGroupTypeConverter();
        typeConvertor.setDbClient(_dbClient);
        typeConvertor.process();

        typeMigrationTest.verifyResults();
    }
}

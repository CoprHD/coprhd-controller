/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server;

import com.emc.storageos.services.util.LoggingUtils;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DB version (db service verion and db schema version) setting tests
 */
public class DbVersionTest extends DbsvcTestBase {
    static {
        LoggingUtils.configureIfNecessary("dbtest-log4j.properties");
    }

    private static final Logger log = LoggerFactory.getLogger(DbVersionTest.class);

    @Test
    public void matchVersion() throws Exception {
        Assert.assertEquals(sourceVersion.getSchemaVersion(), service.getVersion());
    }
}

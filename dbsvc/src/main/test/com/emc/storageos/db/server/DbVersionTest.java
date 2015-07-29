/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
        // make sure that db schema version is "1.1"
        Assert.assertEquals(_dbVersionInfo.getSchemaVersion(), "2.2");

        Assert.assertEquals(_dbVersionInfo.getSchemaVersion(), service.getVersion());
    }
}

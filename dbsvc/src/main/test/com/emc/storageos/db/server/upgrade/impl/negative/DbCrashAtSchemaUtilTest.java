/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.negative;

import java.lang.reflect.Method;

import org.junit.Test;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.KeyspaceMetadata;
import com.emc.storageos.db.server.impl.SchemaUtil;

import static com.emc.storageos.db.server.upgrade.util.DbSchemaChanger.InjectModeEnum;

/**
 * tests dbsvc crashed when schemautil adding cf in skip upgrade scenarios
 */

public class DbCrashAtSchemaUtilTest extends DbCrashInjectionTestBase {

    @Test
    public void runUpgradeNegativeTest() throws Exception {
        Method method = SchemaUtil.class.getDeclaredMethod("checkCf", new Class[] {
                KeyspaceMetadata.class, Cluster.class });
        method.setAccessible(true);
        upgradeNegativeTest(method, InjectModeEnum.AFTER);
    }
}

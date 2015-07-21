/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.negative;

import org.junit.Test;
import java.lang.reflect.Method;

import com.emc.storageos.db.common.diff.DbSchemasDiff;
import com.emc.storageos.db.server.impl.MigrationHandlerImpl;
import static com.emc.storageos.db.server.upgrade.util.DbSchemaChanger.InjectModeEnum;

/**
 * tests dbsvc crashed when migrating in skip upgrade scenarios
 */    

public class DbCrashAtMigrationHandlerTest extends DbCrashInjectionTestBase {

    @Test
    public void runUpgradeNegativeTest() throws Exception {
        Method method = MigrationHandlerImpl.class.getDeclaredMethod(
                "runMigrationCallbacks", new Class[] {DbSchemasDiff.class, String.class, 
                String.class});
        upgradeNegativeTest(method, InjectModeEnum.AFTER);  
    }
}

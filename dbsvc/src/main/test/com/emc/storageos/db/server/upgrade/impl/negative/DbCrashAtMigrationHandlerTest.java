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

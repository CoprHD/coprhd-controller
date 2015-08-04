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

import java.lang.reflect.Method;

import com.netflix.astyanax.ddl.KeyspaceDefinition;
import com.netflix.astyanax.AstyanaxContext;
import org.junit.Test;

import com.emc.storageos.db.server.impl.SchemaUtil;
import static com.emc.storageos.db.server.upgrade.util.DbSchemaChanger.InjectModeEnum;

/**
 * tests dbsvc crashed when schemautil adding cf in skip upgrade scenarios
 */

public class DbCrashAtSchemaUtilTest extends DbCrashInjectionTestBase {

    @Test
    public void runUpgradeNegativeTest() throws Exception {
        Method method = SchemaUtil.class.getDeclaredMethod("checkCf", new Class[] {
                KeyspaceDefinition.class, AstyanaxContext.class });
        method.setAccessible(true);
        upgradeNegativeTest(method, InjectModeEnum.AFTER);
    }
}

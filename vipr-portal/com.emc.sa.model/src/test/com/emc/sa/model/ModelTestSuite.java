/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import com.emc.sa.model.suite.ApprovalRequestTest;
import com.emc.sa.model.suite.CatalogCategoryTest;
import com.emc.sa.model.suite.CatalogServiceFieldTest;
import com.emc.sa.model.suite.CatalogServiceTest;
import com.emc.sa.model.suite.ExecutionLogTest;
import com.emc.sa.model.suite.ExecutionStateTest;
import com.emc.sa.model.suite.ExecutionTaskLogTest;
import com.emc.sa.model.suite.ExecutionWindowTest;
import com.emc.sa.model.suite.OrderParameterTest;
import com.emc.sa.model.suite.OrderTest;
import com.emc.sa.model.suite.SortedIndexTest;
import com.emc.sa.model.suite.VirtualMachineTest;
import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.server.DbsvcTestBase;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        ApprovalRequestTest.class,
        CatalogCategoryTest.class,
        CatalogServiceFieldTest.class,
        CatalogServiceTest.class,
        ExecutionLogTest.class,
        ExecutionStateTest.class,
        ExecutionTaskLogTest.class,
        ExecutionWindowTest.class,
        OrderParameterTest.class,
        OrderTest.class,
        SortedIndexTest.class,
        VirtualMachineTest.class
})
public class ModelTestSuite extends DbsvcTestBase {
    public static CoordinatorClient getCoordinator() {
        return _coordinator;
    }

    public static DbVersionInfo getDbVersionInfo() {
        return sourceVersion;
    }
}

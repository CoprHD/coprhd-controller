/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client;

import org.junit.Test;

public class ViPRCatalogRemoveBlockSnapshotTest {

    @Test
    public void runTest() {
        ClientConfig config = new ClientConfig();

        config.setHost("");
        // config.setPort(port);

        ViPRCatalogClient2 catalogClient = new ViPRCatalogClient2(config);
    }
}

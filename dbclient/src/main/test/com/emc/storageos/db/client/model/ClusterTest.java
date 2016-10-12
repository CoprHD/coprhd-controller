/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import org.junit.Assert;
import org.junit.Test;

public class ClusterTest {

    @Test
    public void testClusterAutoExportEnabled() {
        Cluster cluster = new Cluster();
        Assert.assertFalse(cluster.getAutoExportEnabled());
        cluster.setAutoExportEnabled(false);
        Assert.assertFalse(cluster.getAutoExportEnabled());
        cluster.setAutoExportEnabled(true);
        Assert.assertTrue(cluster.getAutoExportEnabled());
    }

}

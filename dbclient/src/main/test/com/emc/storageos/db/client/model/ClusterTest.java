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
        Assert.assertTrue(cluster.isAutoExportEnabled());
        cluster.setAutoExportEnabled(true);
        Assert.assertTrue(cluster.isAutoExportEnabled());
        cluster.setAutoExportEnabled(false);
        Assert.assertFalse(cluster.isAutoExportEnabled());
    }

    @Test
    public void testClusterAutoUnexportEnabled() {
        Cluster cluster = new Cluster();
        Assert.assertTrue(cluster.isAutoUnexportEnabled());
        cluster.setAutoUnexportEnabled(true);
        Assert.assertTrue(cluster.isAutoUnexportEnabled());
        cluster.setAutoUnexportEnabled(false);
        Assert.assertFalse(cluster.isAutoUnexportEnabled());
    }

}

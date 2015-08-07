/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common;

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.util.KeyspaceUtil;

public class KeyspaceUtilsTest {

    @Test
    public void test() {

        Assert.assertFalse(KeyspaceUtil.isLocal(Project.class));
        Assert.assertTrue(KeyspaceUtil.isLocal(FileShare.class));

        Assert.assertTrue(KeyspaceUtil.isGlobal(Project.class));
        Assert.assertFalse(KeyspaceUtil.isGlobal(FileShare.class));

    }

}

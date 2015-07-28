/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

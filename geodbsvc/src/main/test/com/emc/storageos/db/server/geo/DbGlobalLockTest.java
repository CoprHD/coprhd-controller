/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.geo;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.GlobalLockImpl;
import com.emc.storageos.db.client.model.*;

/**
 * DB global lock tests
 */
public class DbGlobalLockTest extends DbsvcGeoTestBase {
    private static final Logger _logger = LoggerFactory.getLogger(DbGlobalLockTest.class);

    @Test
    public void testNodesvcSharedGlobalLock() throws Exception {
        // NodeSvcShared MODE

        _logger.info("Starting node/svc shared global lock test");

        DbClientImpl dbClient = (DbClientImpl) getDbClient();

        boolean flag;

        String vdc = "vdc1";

        // 1. basic test
        String lockname = "testlock1";
        GlobalLockImpl glock = new GlobalLockImpl(dbClient, lockname, GlobalLock.GL_Mode.GL_NodeSvcShared_MODE, 0, vdc);

        // acquire the lock
        flag = glock.acquire("vipr1");
        Assert.assertTrue(flag);

        // get lock owner
        String owner = null;
        owner = glock.getOwner();
        Assert.assertEquals(owner, "vdc1:vipr1");

        // re-enter the lock again with the same owner
        flag = glock.acquire("vipr1");
        Assert.assertTrue(flag);

        // try to acquire the lock with a different owner
        GlobalLockImpl glock2 = new GlobalLockImpl(dbClient, lockname, GlobalLock.GL_Mode.GL_NodeSvcShared_MODE, 0, vdc);
        flag = glock2.acquire("vipr2");
        Assert.assertFalse(flag);

        // try to release the lock with a different owner
        flag = glock2.release("vipr2");
        Assert.assertFalse(flag);

        // release the lock
        flag = glock.release("vipr1");
        Assert.assertTrue(flag);

        // 2. timeout test
        glock = new GlobalLockImpl(dbClient, lockname, GlobalLock.GL_Mode.GL_NodeSvcShared_MODE, 3000, vdc);
        // acquire the lock
        flag = glock.acquire("vipr1");
        Assert.assertTrue(flag);

        // try to acquire the lock with a different owner
        glock2 = new GlobalLockImpl(dbClient, lockname, GlobalLock.GL_Mode.GL_NodeSvcShared_MODE, 3000, vdc);
        flag = glock2.acquire("vipr2");
        Assert.assertFalse(flag);

        // acquire the lock (expired) again with a different owner
        Thread.sleep(3000);
        flag = glock2.acquire("vipr2");
        Assert.assertTrue(flag);

        // try to release with original owner
        flag = glock.release("vipr1");
        Assert.assertFalse(flag);

        // release with current owner
        flag = glock2.release("vipr2");
        Assert.assertTrue(flag);
    }

    @Test
    public void testVdcSharedGlobalLock() throws Exception {
        // VdcShared MODE
        _logger.info("Starting vdc shared global lock test");

        DbClientImpl dbClient = (DbClientImpl) getDbClient();

        boolean flag;

        String vdc = "vdc1";

        String lockname = "testlock2";
        GlobalLockImpl glock = new GlobalLockImpl(dbClient, lockname, GlobalLock.GL_Mode.GL_VdcShared_MODE, 0, vdc);

        // acquire the lock
        flag = glock.acquire("vipr1");
        Assert.assertTrue(flag);

        // get lock owner
        String owner = null;
        owner = glock.getOwner();
        Assert.assertEquals(owner, vdc);

        // re-enter the lock again with the same owner
        flag = glock.acquire("vipr2");
        Assert.assertTrue(flag);

        // acquire the lock with a different local owner within the same Vdc
        GlobalLockImpl glock2 = new GlobalLockImpl(dbClient, lockname, GlobalLock.GL_Mode.GL_VdcShared_MODE, 0, vdc);
        flag = glock2.acquire("vipr2");
        Assert.assertTrue(flag);

        // try to acquire the lock from a different vdc
        GlobalLockImpl glock3 = new GlobalLockImpl(dbClient, lockname, GlobalLock.GL_Mode.GL_VdcShared_MODE, 0, "vdc2");
        flag = glock3.acquire("vipr2");
        Assert.assertFalse(flag);

        // release the lock from vipr2
        flag = glock2.release("vipr2");
        Assert.assertTrue(flag);

        // get lock owner
        owner = glock.getOwner();
        Assert.assertEquals(owner, vdc);

        // release the lock from vipr1
        flag = glock.release("vipr1");
        Assert.assertTrue(flag);

        // get lock owner again
        owner = glock.getOwner();
        Assert.assertEquals(owner, null);
    }
}

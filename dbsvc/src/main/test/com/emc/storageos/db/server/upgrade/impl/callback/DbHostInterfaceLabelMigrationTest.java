/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.BeforeClass;

import org.junit.Assert;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.HostInterfaceLabelMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class DbHostInterfaceLabelMigrationTest extends DbSimpleMigrationTestBase {

    private final int INSTANCES_TO_CREATE = 10;

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new HostInterfaceLabelMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "1.1";
    }

    @Override
    protected String getTargetVersion() {
        return "2.0";
    }

    @Override
    protected void prepareData() throws Exception {
        prepareIpInterfaceData();
        prepareInitiatorData();

    }

    @Override
    protected void verifyResults() throws Exception {
        verifyIpInterfaceData();
        verifyInitiatorData();
    }

    private void prepareIpInterfaceData() {
        for (int i = 0; i < INSTANCES_TO_CREATE / 2; i++) {
            IpInterface ipinterface = new IpInterface();
            ipinterface.setId(URIUtil.createId(IpInterface.class));
            ipinterface.setIpAddress("10.0.0." + i);
            _dbClient.createObject(ipinterface);
        }

        for (int i = 0; i < INSTANCES_TO_CREATE / 2; i++) {
            IpInterface ipinterface = new IpInterface();
            ipinterface.setId(URIUtil.createId(IpInterface.class));
            ipinterface.setHost(URIUtil.createId(Host.class));
            ipinterface.setIpAddress("10.0.1." + i);
            ipinterface.setLabel("label" + i);
            _dbClient.createObject(ipinterface);
        }

        List<URI> list = _dbClient.queryByType(IpInterface.class, false);
        int count = 0;
        for (@SuppressWarnings("unused")
        URI ignore : list) {
            count++;
        }

        Assert.assertTrue("Expected " + INSTANCES_TO_CREATE + " prepared " + IpInterface.class.getSimpleName() + ", found only " + count,
                count == INSTANCES_TO_CREATE);
    }

    private void prepareInitiatorData() {
        for (int i = 0; i < INSTANCES_TO_CREATE / 2; i++) {
            Initiator initiator = new Initiator();
            initiator.setId(URIUtil.createId(Initiator.class));
            initiator.setInitiatorPort("10:00:00:00:" + i);
            _dbClient.createObject(initiator);
        }

        for (int i = 0; i < INSTANCES_TO_CREATE / 2; i++) {
            Initiator initiator = new Initiator();
            initiator.setId(URIUtil.createId(Initiator.class));
            initiator.setHost(URIUtil.createId(Host.class));
            initiator.setInitiatorPort("10:00:00:01:" + i);
            initiator.setLabel("label" + i);
            _dbClient.createObject(initiator);
        }

        List<URI> list = _dbClient.queryByType(Initiator.class, false);
        int count = 0;
        for (@SuppressWarnings("unused")
        URI ignore : list) {
            count++;
        }

        Assert.assertTrue("Expected " + INSTANCES_TO_CREATE + " prepared " + Initiator.class.getSimpleName() + ", found only " + count,
                count == INSTANCES_TO_CREATE);
    }

    private void verifyIpInterfaceData() {
        List<URI> list = _dbClient.queryByType(IpInterface.class, false);
        int count = 0;

        Iterator<IpInterface> objs = _dbClient.queryIterativeObjects(IpInterface.class, list);
        while (objs.hasNext()) {
            IpInterface ipinterface = objs.next();
            count++;
            Assert.assertNotNull("Label for ipInterface shouldn't be null", ipinterface.getLabel());

            if (ipinterface.getHost() != null) {
                Assert.assertNotSame("Label should not be equal to the ipAddress", ipinterface.getLabel(), ipinterface.getIpAddress());
            } else {
                Assert.assertEquals("Label should equal to ipAddress", ipinterface.getLabel(), ipinterface.getIpAddress());
            }
        }

        Assert.assertTrue("We should still have " + INSTANCES_TO_CREATE + " " + IpInterface.class.getSimpleName()
                + " after migration, not " + count, count == INSTANCES_TO_CREATE);
    }

    private void verifyInitiatorData() {
        List<URI> list = _dbClient.queryByType(Initiator.class, false);
        int count = 0;

        Iterator<Initiator> objs = _dbClient.queryIterativeObjects(Initiator.class, list);
        while (objs.hasNext()) {
            Initiator initiator = objs.next();
            count++;
            Assert.assertNotNull("Label initiator shouldn't be null", initiator.getLabel());

            if (initiator.getHost() != null) {
                Assert.assertNotSame("Label should not be equal to the port", initiator.getLabel(), initiator.getInitiatorPort());
            } else {
                Assert.assertEquals("Label should equal to port", initiator.getLabel(), initiator.getInitiatorPort());
            }
        }

        Assert.assertTrue("We should still have " + INSTANCES_TO_CREATE + " " + Initiator.class.getSimpleName() + " after migration, not "
                + count, count == INSTANCES_TO_CREATE);
    }

}

/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.computecontroller.impl.ucs;

import java.net.URI;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.exceptions.DeviceControllerException;

public class TaskUpdateTest {
    private static final Logger _logger = LoggerFactory.getLogger(TaskUpdateTest.class);

    private DbClient _dbClient = null;

    private String opId = "bff6aa90-5fee-4142-aeaf-99c99a6524fa";

    private URI dataObjectURI = URI.create("urn:storageos:Host:fb498093-003c-4621-88c0-02460faa1742:vdc1");

    private Class<Host> dataObjectClass = Host.class;

    @Before
    public void setup() {
        // get DB client
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("dbclient-conf.xml");
        _dbClient = (DbClient) ctx.getBean("dbclient");
        _dbClient.start();
    }

    @After
    public void cleanup() {
        if (_dbClient != null) {
            _dbClient.stop();
        }
    }

    /*
     * Create Volume/BlockSnapshot for all Storage Systems
     */
    @Test
    public void failTask() {
        _dbClient.error(Host.class, dataObjectURI, opId, new DeviceControllerException(new IllegalStateException(
                "Cancelling task")));

    }

}
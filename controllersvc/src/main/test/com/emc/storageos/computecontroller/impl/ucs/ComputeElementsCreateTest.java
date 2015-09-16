/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.computecontroller.impl.ucs;

import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeSystem;

/*
 * Test class to test RefreshRequiredUpdateFunction.call() with simulated objects
 * It uses configuration files from dbclient, dbutils and coordinatorsvc
 */
public class ComputeElementsCreateTest {
    private DbClient _dbClient = null;

    private static final int computeElementCount = 5;

    // Storage System URI used to create RefreshRequiredUpdateFunction instance
    private URI computeSystemURI = null;

    private List<ComputeElement> computeElementsObjects = new ArrayList<ComputeElement>();

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
    public void createComputeElements() {

        List<URI> computeSystems = _dbClient.queryByType(ComputeSystem.class, true);

        while (computeSystems.iterator().hasNext()) {
            computeSystemURI = computeSystems.iterator().next();
        }

        for (int i = 0; i < computeElementCount; i++) {
            URI computeElementURI = URIUtil.createId(ComputeElement.class);

            ComputeElement computeElement = new ComputeElement();
            computeElement.setComputeSystem(computeSystemURI);
            computeElement.setId(computeElementURI);
            computeElement.setCreationTime(Calendar.getInstance());
            computeElement.setInactive(false);
            computeElement.setRam(67108864L);
            computeElement.setNativeGuid(computeSystemURI.toASCIIString());
            computeElementsObjects.add(computeElement);

        }
        _dbClient.createObject(computeElementsObjects);
    }

    /*
     * Verify a list of block objects of given type
     */
    @Test
    public void deleteComputeElementObjects() {
        // delete all objects in the list
        _dbClient.removeObject(computeElementsObjects.toArray(new ComputeElement[computeElementsObjects.size()]));
    }
}
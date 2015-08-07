/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cimadapter.connections.cim;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;

import org.junit.Assert;
import org.junit.Test;

/**
 * JUnit test enum for {@link CimQueuedIndication}.
 */
public class CimQueuedIndicationTest {

    private static final String OBJ_PATH_CLASS_NAME = "ObjPathClassName";
    private static final String INDICATION_URL = "indicationUrl";

    // CIM instance reference.
    private CIMInstance _instance = null;

    /**
     * Tests the CimQueuedIndication class.
     */
    @SuppressWarnings("rawtypes")
    @Test
    public void testCimQueuedIndication() {
        CIMObjectPath objPath = CimObjectPathCreator.createInstance(OBJ_PATH_CLASS_NAME);
        CIMProperty[] properties = new CIMProperty[0];
        _instance = new CIMInstance(objPath, properties);
        CimQueuedIndication indication = new CimQueuedIndication(INDICATION_URL,
            _instance);
        Assert.assertEquals(indication.getURL(), INDICATION_URL);
        Assert.assertEquals(indication.getIndication(), _instance);
    }
}

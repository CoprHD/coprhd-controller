/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cimadapter.connections.cim;

import javax.cim.CIMDataType;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;

import org.junit.Assert;
import org.junit.Test;

/**
 * JUnit test enum for {@link CimCompositeID}.
 */
public class CimCompositeIDTest {

    public static final String OBJ_PATH_NAME = "SourceInstanceModelPath";
    public static final String OBJ_PATH_PROP1_NAME = "Prop1";
    public static final String OBJ_PATH_PROP1_VALUE = "Value1";
    public static final String OBJ_PATH_PROP2_NAME = "Prop2ClassName";
    public static final String OBJ_PATH_PROP2_VALUE = "Value2";
    public static final String OBJ_PATH_PROP3_NAME = "Prop3";
    public static final String OBJ_PATH_PROP3_VALUE = "Value3";
    public static final String NAME_SPACE = "interop";

    /**
     * Tests the CimCompositeID constructor.
     */
    @SuppressWarnings("rawtypes")
    @Test
    public void testCimCompositeID() {

        CIMProperty[] properties = new CIMProperty[] {
            new CIMProperty<String>(OBJ_PATH_PROP1_NAME, CIMDataType.STRING_T,
                OBJ_PATH_PROP1_VALUE),
            new CIMProperty<String>(OBJ_PATH_PROP2_NAME, CIMDataType.STRING_T,
                OBJ_PATH_PROP2_VALUE),
            new CIMProperty<String>(OBJ_PATH_PROP3_NAME, CIMDataType.STRING_T,
                OBJ_PATH_PROP3_VALUE) };
        CIMObjectPath objPath = CimObjectPathCreator.createInstance(OBJ_PATH_NAME, NAME_SPACE, properties);
        CimCompositeID compositeId = new CimCompositeID(objPath);
        Assert.assertEquals(compositeId.toString(), OBJ_PATH_PROP3_VALUE + "/"
            + OBJ_PATH_PROP1_VALUE);
    }
}

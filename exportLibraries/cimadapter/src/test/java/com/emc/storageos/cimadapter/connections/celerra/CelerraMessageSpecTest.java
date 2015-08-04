/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cimadapter.connections.celerra;

import org.junit.Assert;
import org.junit.Test;

/**
 * JUnit test class for {@link CelerraMessageSpec}.
 */
public class CelerraMessageSpecTest {
    
    private static final String SPEC_ID = "CS_CORE,CIC,1";
    private static final String SPEC_COMP = "CS_CORE";
    private static final String SPEC_FACILITY = "CIC";
    private static final String SPEC_EVENT_ID = "1";
    private static final String SPEC_SEVERITY = "ERROR";
    private static final String SPEC_PATTERN = "^IPC\\s+failure\\s+detected\\.\\s+[^\\.]+\\.";

    /**
     * Tests the CelerraMessageSpec getters/setters.
     */
    @Test
    public void testCelerraMessageSpec() {
        CelerraMessageSpec msgSpec = new CelerraMessageSpec();
        msgSpec.setID(SPEC_ID);
        msgSpec.setComponent(SPEC_COMP);
        msgSpec.setFacility(SPEC_FACILITY);
        msgSpec.setEventID(SPEC_EVENT_ID);
        msgSpec.setSeverity(SPEC_SEVERITY);
        msgSpec.setPattern(SPEC_PATTERN);
        
        Assert.assertEquals(msgSpec.getID(), SPEC_ID);
        Assert.assertEquals(msgSpec.getComponent(), SPEC_COMP);
        Assert.assertEquals(msgSpec.getFacility(), SPEC_FACILITY);
        Assert.assertEquals(msgSpec.getEventID(), SPEC_EVENT_ID);
        Assert.assertEquals(msgSpec.getSeverity(), SPEC_SEVERITY);
        Assert.assertEquals(msgSpec.getPattern(), SPEC_PATTERN);        
    }
}

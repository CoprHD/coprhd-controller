/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.cimadapter.consumers;

import org.junit.Test;
import org.junit.Assert;

import com.emc.storageos.cimadapter.processors.CimIndicationProcessor;

/**
 * JUnit test class for {@link CimIndicationConsumer}.
 */
public class CimIndicationConsumerTest {

    /**
     * Tests the setIndicationProcessor method.
     */
    @Test
    public void testSetIndicationProcessor() {
        CimIndicationProcessor processor = new TestIndicationProcessor();
        CimIndicationConsumer consumer = new FileCimIndicationConsumer();
        consumer.setIndicationProcessor(processor);
        Assert.assertEquals(processor, consumer.getIndicationProcessor());
    }
    
    /**
     * Tests the setUseDefaultProcessor method.
     */
    @Test
    public void testSetUseDefaultProcessorr() {
        CimIndicationConsumer consumer = new FileCimIndicationConsumer();
        consumer.setUseDefaultProcessor(true);
        Assert.assertEquals(consumer.getUseDefaultProcessor(), true);
        consumer.setUseDefaultProcessor(false);
        Assert.assertEquals(consumer.getUseDefaultProcessor(), false);
    }    
    
    /**
     * Private processor for testing purposes.
     */
    private class TestIndicationProcessor extends CimIndicationProcessor {
        public Object process(Object indication) {
            return null;
        }
    }
}

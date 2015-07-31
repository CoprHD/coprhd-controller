/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
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

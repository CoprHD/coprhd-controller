/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.zookeeper;

import java.util.concurrent.atomic.AtomicLong;

import com.emc.sa.model.dao.ModelClient;

/**
 */
public class InMemoryOrderNumberSequence implements OrderNumberSequence {
    private AtomicLong currentOrderNumber;

    private ModelClient MODELS;

    public InMemoryOrderNumberSequence(ModelClient modelClient) {
        MODELS = modelClient;

        // Initialize ourselves off the database
    }

    @Override
    public long nextOrderNumber() {
    	return currentOrderNumber.getAndIncrement();
    }
}

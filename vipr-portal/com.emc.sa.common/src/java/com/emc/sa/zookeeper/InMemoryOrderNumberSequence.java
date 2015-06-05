/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.zookeeper;

import com.emc.sa.model.dao.ModelClient;

/**
 */
public class InMemoryOrderNumberSequence implements OrderNumberSequence {
    private Long currentOrderNumber;

    private ModelClient MODELS;

    public InMemoryOrderNumberSequence(ModelClient modelClient) {
        MODELS = modelClient;

        // Initialize ourselves off the database
    }

    @Override
    public long nextOrderNumber() {
        synchronized (currentOrderNumber) {
            return currentOrderNumber++;
        }
    }
}

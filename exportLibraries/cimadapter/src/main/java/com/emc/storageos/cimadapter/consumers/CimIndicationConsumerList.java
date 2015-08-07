/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cimadapter.consumers;

// Java imports
import java.util.ArrayList;
import java.util.Collection;

/**
 * An alias for the ArrayList for use with Spring configuration of the
 * indication consumers.
 */
public class CimIndicationConsumerList extends ArrayList<CimIndicationConsumer> {

    // For serializable class.
    private static final long serialVersionUID = 1L;

    /**
     * Spring-friendly constructor
     * 
     * @param consumers The collection of indication consumers.
     */
    public CimIndicationConsumerList(Collection<CimIndicationConsumer> consumers) {
        super(consumers);
    }
}
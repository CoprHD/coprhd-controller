/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
// Copyright 2012 by EMC Corporation ("EMC").
//
// UNPUBLISHED  CONFIDENTIAL  AND  PROPRIETARY  PROPERTY OF EMC. The copyright
// notice above does not evidence any actual  or  intended publication of this
// software. Disclosure and dissemination are pursuant to separate agreements.
// Unauthorized use, distribution or dissemination are strictly prohibited.

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
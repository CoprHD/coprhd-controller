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

package com.emc.storageos.cimadapter.processors;

/**
 * Defines an interface for classes that implement CIM indication processors. A
 * processor can be associated with an {@link CimIndicationConsumer}. Before the
 * indication is forwarded to the consumer the indication will be processed by
 * the processor associated with that consumer. The processed indication in the
 * format expected by the consumer is then forwarded to the consumer.
 */
public abstract class CimIndicationProcessor {

    /**
     * Called to process the passed CIM indication to a desired format.
     * 
     * Note that the passed indication should be in the format expected by the
     * processor.
     * 
     * @param indication The indication to be processed in the format expected
     *        by the processor.
     */
    public abstract Object process(Object indication);
}
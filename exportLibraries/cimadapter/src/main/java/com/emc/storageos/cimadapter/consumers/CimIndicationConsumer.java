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

// StorageOS imports
import com.emc.storageos.cimadapter.processors.CimIndicationProcessor;

/**
 * Defines an abstract base class for derived classes that wish to be notified
 * of indications that are received from the connections being managed by the {@link ConnectionManager}.
 */
public abstract class CimIndicationConsumer {

    // Reference to an optional indication processor for the consumer.
    private CimIndicationProcessor _indicationProcessor = null;

    // Flag indicates whether or not the indication should be processed using
    // the default indication processor. If true and a custom processor has
    // been specified for the consumer, the default processing will occur
    // first and the result is then passed through the custom processor.
    private boolean _useDefaultProcessor = false;

    /**
     * Called when an indication is received by the {@link CimListener}. Note
     * that if a processor was specified and/or the use default processor flag
     * is true, then the passed indication data is in a format determined by the
     * specified processing. If no processing is specified the passed object is
     * an instance of CIMInstance.
     * 
     * @param indicationData The data for the indication to be consumed.
     */
    public abstract void consumeIndication(Object indicationData);

    /**
     * Getter for the optional indication processor for the consumer.
     * 
     * @return The indication processor for the consumer, or null if not set.
     */
    public CimIndicationProcessor getIndicationProcessor() {
        return _indicationProcessor;
    }

    /**
     * Setter for the optional indication processor for the consumer.
     * 
     * @param indicationProcessor The indication processor for the consumer.
     */
    public void setIndicationProcessor(CimIndicationProcessor indicationProcessor) {
        _indicationProcessor = indicationProcessor;
    }

    /**
     * Getter for the flag indicating whether or not default processor should be
     * used.
     * 
     * @return true for default indication processing, false otherwise.
     */
    public boolean getUseDefaultProcessor() {
        return _useDefaultProcessor;
    }

    /**
     * Setter for the the flag indicating whether or not default indication
     * processing should be used.
     * 
     * @param useDefaultProcessor true for default processing, false otherwise..
     */
    public void setUseDefaultProcessor(boolean useDefaultProcessor) {
        _useDefaultProcessor = useDefaultProcessor;
    }
}
/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.monitoring.cim;

// Java imports
import java.util.Calendar;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.cimadapter.consumers.CimIndicationConsumer;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.ArrivedIndication;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.CIMIndicationProcessor;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.IntermediateProcessor;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;

/**
 * An indication consumer that writes the received indications to Cassandra.
 * This consumer expects the passed indication to be in the form of name/value
 * pairs passed in a Hashtable<String, String>.
 */

public class CassandraCimIndicationConsumer extends CimIndicationConsumer {

    /**
     * A thread safe queue that maintains the list of indications
     */
    private ConcurrentLinkedQueue<ArrivedIndication> list;

    private int _indicationBufferLimit;

    /**
     * A reference to Scheduler service
     */
    private ScheduledExecutorService indicationProcessTimer;

    /**
     * The delay between the termination of one execution and the commencement
     * of the next
     */
    private static final int PERIODIC_DELAY = 30;

    /**
     * Processor Interface
     */
    @Autowired
    private CIMIndicationProcessor _processor;

    /**
     * Logger to log the debug statements
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(CassandraCimIndicationConsumer.class);

    /**
     * Reference to MonitoringPropertiesLoader
     */
    @Autowired
    private MonitoringPropertiesLoader _monitoringPropertiesLoader;

    /**
     * Default Constructor
     */
    public CassandraCimIndicationConsumer() {
        super();
    }

    public void setIndicationBufferLimit(int indicationBufferLimit) {
        _indicationBufferLimit = indicationBufferLimit;
    }

    /**
     * Will get called during construction phase, configured through spring xml
     * configuration
     */
    private void init() {
        _logger.debug("Initializing....");
        list = new ConcurrentLinkedQueue<ArrivedIndication>();
        IntermediateProcessor intermediateProcessor = new IntermediateProcessor(
                list, _processor);
        indicationProcessTimer = Executors.newSingleThreadScheduledExecutor();
        indicationProcessTimer.scheduleWithFixedDelay(intermediateProcessor,
                CIMConstants.INDICATION_PROCESS_INTERVAL, PERIODIC_DELAY, TimeUnit.SECONDS);
    }

    /**
     * Expects the data as a hashtable of name/value pairs. Start process the
     * indication received using CIMIndication Processor.
     * 
     * @param indicationData
     */
    @Override
    public void consumeIndication(Object indicationData) {

        if (indicationData == null) {
            _logger.error("CIMIndication consumer received null data.");
            return;
        }

        if (!(indicationData instanceof Hashtable<?, ?>)) {
            _logger.error("CIMIndication consumer expects a hashtable of name/value pairs.");
            return;
        }

        try {

            @SuppressWarnings("unchecked")
            Hashtable<String, String> indicationsTable = (Hashtable<String, String>) indicationData;
            if (_monitoringPropertiesLoader.isToLogIndications()) {
                _logger.debug(
                        "Indication received to CassandraCimIndicationConsumer to process, with size of key value pairs : {}",
                        indicationsTable.size());
            }

            // Q
            ArrivedIndication indication = new ArrivedIndication(
                    indicationsTable, Calendar.getInstance().getTimeInMillis());
            if (list.size() < _indicationBufferLimit) {
                list.add(indication);
                _logger.debug("Indication Queued at {}", list.size());
            } else {
                _logger.warn("Indication dropped. Indication buffer reached  max. capacity. Buffer size: {}", list.size());
                _logger.info("Indication with key : value pairs dropped --> \n"
                        + CIMIndicationProcessor.getIndicationData(indication.getIndication()));
            }
        } catch (Exception e) {
            _logger.error("Exception while processing the indication", e);
        }
    }
}

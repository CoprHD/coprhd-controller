/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;

/**
 * Periodic timer that executes this class to process the indications sits in
 * the queue.
 * 
 */
public class IntermediateProcessor implements Runnable {

    /**
     * Processor Interface
     */
    private CIMIndicationProcessor _processor;

    /**
     * Logger to log the debug statements
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(IntermediateProcessor.class);

    /**
     * A reference to the list of indications
     */
    private ConcurrentLinkedQueue<ArrivedIndication> _list;

    /**
     * A reference to DateFormatter
     */
    private SimpleDateFormat format = new SimpleDateFormat(
            "EEE, d MMM yyyy HH:mm:ss");

    /**
     * @param list
     */
    public IntermediateProcessor(ConcurrentLinkedQueue<ArrivedIndication> list,
            CIMIndicationProcessor processor) {
        _list = list;
        _processor = processor;
    }

    @Override
    public void run() {

        if (_processor == null) {
            _logger.error("Unable to initialize the processors, Check Spring Configuration");
            return;
        }

        int index = 0;
        _logger.debug("Timer awoke, iterating indications with size {}",
                _list.size());
        for (ArrivedIndication indication : _list) {

            long duration = TimeUnit.MILLISECONDS.toMinutes(Calendar
                    .getInstance().getTimeInMillis())
                    - TimeUnit.MILLISECONDS.toMinutes(indication
                            .getArrivalTime());

            _logger.debug(
                    " {}. Arrival Time {} - Sit in Q for {} minute(s)",
                    new Object[] { ++index,
                            printArrivalTime(indication.getArrivalTime()),
                            duration });

            if (duration >= CIMConstants.INDICATION_PROCESS_INTERVAL) {
                _processor.processIndication(indication.getIndication());
                _list.remove(indication);
            }

        }
        _logger.debug(
                "Indication processing cycle finished. {} indications left to process",
                _list.size());
    }

    /**
     * Prints the long time in readable format
     * 
     * @param arrivalTime
     * @return
     */
    public synchronized String printArrivalTime(long arrivalTime) {
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(arrivalTime);
        return format.format(time.getTime());
    }
}

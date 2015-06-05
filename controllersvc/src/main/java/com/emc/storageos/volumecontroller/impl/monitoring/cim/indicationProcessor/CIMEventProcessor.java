/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor;

import java.util.Hashtable;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CIMEventProcessor extends BaseProcessor{

    /**
     * Logger to log the debug statements
     */
    private static final Logger _logger = LoggerFactory.getLogger(CIMEventProcessor.class);

    /**
     * Type of all event processors will be holding this map
     */
    private Map<String, BaseProcessor> _eventProcessors;

    /**
     * Default Constructor
     */
    public CIMEventProcessor(Map<String, BaseProcessor> eventProcessors) {
        _eventProcessors = eventProcessors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processIndication(Hashtable<String, String> notification) {
        String eventType = getEventType(notification);
        BaseProcessor processor = _eventProcessors.get(eventType);
        if (processor != null) {
            processor.processIndication(notification);
        } else {
            _logger.debug("No processor found to process this indication");
        }
    }
}

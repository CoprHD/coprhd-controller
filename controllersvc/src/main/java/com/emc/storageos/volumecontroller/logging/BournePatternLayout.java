/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.logging;

import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.PatternParser;

/**
 * Custom log4j PatternLayout is created to support custom arguments in the
 * layout ConversionPattern that allow the log message to specify customized
 * data in logged messages for example, the id of a provisioning operation 
 * executed by the controller service as well as the id resource impacted by
 * that operation.
 */
public class BournePatternLayout extends PatternLayout {

    /**
     * {@inheritDoc}
     */
    @Override
    protected PatternParser createPatternParser(String pattern) {
        return new BournePatternParser(pattern);
    }
}

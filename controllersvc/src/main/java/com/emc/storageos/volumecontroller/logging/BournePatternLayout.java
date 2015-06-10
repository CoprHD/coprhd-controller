/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

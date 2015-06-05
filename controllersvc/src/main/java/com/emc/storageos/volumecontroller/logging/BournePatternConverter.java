/**
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

import java.util.ArrayList;

import org.apache.log4j.helpers.PatternConverter;
import org.apache.log4j.spi.LoggingEvent;

/**
 * A custom PatternConverter that is invoked when a supported, custom character
 * appears in the ConversionPattern for a BournePatternLayout. The
 * BourbePatternConverter can return the id of a provisioning operation executed
 * by the controller service in the current thread as well as the id of the
 * resource impacted by the provisioning operation. If there is no operation
 * and/or resource information for the current thread, then the PatternConverter
 * simply returns a blank String.
 */
public class BournePatternConverter extends PatternConverter {

    // Delimiter character used to separate pattern data values.
    private static final char DATA_DELIM = '|';

    // The pattern data for a given thread is a list of strings to be included
    // in the log message.
    public static ThreadLocal<ArrayList<String>> s_patternData = new ThreadLocal<ArrayList<String>>();

    /**
     * {@inheritDoc}
     */
    @Override
    protected String convert(LoggingEvent evt) {
        StringBuilder strBuilder = new StringBuilder();

        ArrayList<String> patternData = s_patternData.get();
        if (patternData != null) {
            for (int i = 0; i < patternData.size(); i++) {
                if (strBuilder.length() != 0) {
                    strBuilder.append(DATA_DELIM);
                }
                strBuilder.append(patternData.get(i));
            }
        }

        return strBuilder.toString();
    }
}

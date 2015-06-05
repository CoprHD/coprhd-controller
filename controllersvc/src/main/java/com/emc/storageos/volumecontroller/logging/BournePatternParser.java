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

import org.apache.log4j.helpers.PatternParser;

/**
 * A custom PatternParser to use for the {@link BournePatternLayout}. We simply
 * extend the base class to specify the custom PatternConverter to use when the
 * BOURNE_PATTERN_CHAR appears in the value of the ConversionPattern for the
 * BournePatternLayout.
 */
public class BournePatternParser extends PatternParser {

    // A custom character that can be included in the value of the
    // ConversionPattern for a BournePatternLayout.
    private static final char BOURNE_PATTERN_CHAR = 'B';

    /**
     * Constructor
     * 
     * @param pattern The value of the ConversionPatterm for a PatternLayout.
     */
    public BournePatternParser(String pattern) {
        super(pattern);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void finalizeConverter(char c) {
        switch (c) {
        case BOURNE_PATTERN_CHAR:
            // We specify the PatternConverter to use for the
            // BOURNE_PATTERN_CHAR when it appears in the value
            // of the ConversionPattern for a BournePatternLayout.
            currentLiteral.setLength(0);
            addConverter(new BournePatternConverter());
            break;
        default:
            super.finalizeConverter(c);
        }
    }
}

/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.scaleio.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class should contain common routines that can be used
 * among the ScaleIOXXX classes.
 *
 */
public class ScaleIOUtils {

    /**
     * This takes a ScaleIO capacity output that looks like "NNNN GB" or "NNNN MB"
     * to a String byte value.
     *
     * @param capacityString - String capacity value that was scraped from ScaleIO CLI output
     * @return String value representing the the capacity value as bytes
     */
    static String convertToBytes(String capacityString) {
        String result = "0";
        ScaleIOContants.PoolCapacityMultiplier converter = ScaleIOContants.PoolCapacityMultiplier.matches(capacityString);
        if (converter != null) {
            String patternString = "(\\d+) " + converter.getPostFix();
            Matcher match = Pattern.compile(patternString).matcher(capacityString);
            if (match.matches()) {
                result = Long.toString(Long.parseLong(match.group(1)) * converter.getMultiplier());
            }
        }
        return result;
    }
}

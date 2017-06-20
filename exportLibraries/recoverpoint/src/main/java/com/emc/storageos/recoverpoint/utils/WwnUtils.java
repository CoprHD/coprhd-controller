/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

public class WwnUtils {

    public static int EXPECTED_WWN_LENGTH = 16;
    
    public enum FORMAT {
        COLON, DASH, COMMA, NOMARKERS
    };

    /**
     * Convert a wwn to a Long value, such as the ones used in DeviceUID in RecoverPoint
     * 
     * @param wwn incoming wwn in any of the supported formats
     * @return Long value
     */
    public static Long convertWWNtoLong(String wwn) {
        // Normalize the string down.
        String norm = wwn.replaceAll(":", "").replaceAll("-", "");
        Long wwnLong = Long.valueOf(norm, 16);
        return wwnLong;
    }

    /**
     * Some APIs uses WWN in the colon format. This utility takes in WWN strings
     * in various formats: 600601606c4a2200
     * 60:06:01:60:6C:4A:22:00
     * 60060160-6c4a-2200 and returns the WWN in a format standard requested
     * 
     * @param wwn
     *            incoming WWN in any of the supported formats
     * @param format
     *            .FORMAT the format output
     * @return wwn string
     */
    public static String convertWWN(String wwn, WwnUtils.FORMAT format) {

        String ret = "";
        
        if (StringUtils.isEmpty(wwn)) {
            return ret;
        }

        // Normalize the string down.
        String norm = wwn.replaceAll(":", "").replaceAll("-", "");

        // If the length is less than EXPECTED_WWN_LENGTH, the value 
        // will not be parsed.
        if (norm.length() < EXPECTED_WWN_LENGTH) {
            return norm;
        }
        
        // Formats such as "600601606c4a2200a03b50228a8edf11"
        if (format == FORMAT.NOMARKERS) {
            return norm;
        } else if (format == FORMAT.DASH) {
            String temp = "" + norm.charAt(0) + norm.charAt(1) + norm.charAt(2)
                    + norm.charAt(3) + norm.charAt(4) + norm.charAt(5)
                    + norm.charAt(6) + norm.charAt(7) + "-" + norm.charAt(8)
                    + norm.charAt(9) + norm.charAt(10) + norm.charAt(11) + "-"
                    + norm.charAt(12) + norm.charAt(13) + norm.charAt(14)
                    + norm.charAt(15);

            // The WWN Java object can deal with upper or lower case,
            // but seems to print lower.
            ret = temp.toLowerCase(Locale.getDefault());
        } else if (format == FORMAT.COLON) {
            String temp = "" + norm.charAt(0) + norm.charAt(1) + ":"
                    + norm.charAt(2) + norm.charAt(3) + ":" + norm.charAt(4)
                    + norm.charAt(5) + ":" + norm.charAt(6) + norm.charAt(7)
                    + ":" + norm.charAt(8) + norm.charAt(9) + ":"
                    + norm.charAt(10) + norm.charAt(11) + ":" + norm.charAt(12)
                    + norm.charAt(13) + ":" + norm.charAt(14) + norm.charAt(15);

            // Naviseccli likes the upper case, in case someone does
            // string comparisons instead of WWN object comparisons
            ret = temp.toUpperCase(Locale.getDefault());
        }
        else if (format == FORMAT.COMMA) {
            String temp = "" + norm.charAt(0) + norm.charAt(1) + ","
                    + norm.charAt(2) + norm.charAt(3) + "," + norm.charAt(4)
                    + norm.charAt(5) + "," + norm.charAt(6) + norm.charAt(7)
                    + "," + norm.charAt(8) + norm.charAt(9) + ","
                    + norm.charAt(10) + norm.charAt(11) + "," + norm.charAt(12)
                    + norm.charAt(13) + "," + norm.charAt(14) + norm.charAt(15);

            // Naviseccli likes the upper case, in case someone does
            // string comparisons instead of WWN object comparisons
            ret = temp.toUpperCase(Locale.getDefault());
        }
        return (ret);
    }

    /**
     * validates that the passed in string represents a valid WWN. WWN must be in the
     * 60060160-6c4a-2200 WWN format
     * 
     * @param wwnString string representation of a WWN
     * @return true if the string represents a valid WWN; otherwise false
     */
    public static boolean isValid(String wwnString) {

        if (wwnString.length() != 18) {
            return false;
        }

        Pattern pattern = Pattern
                .compile("^([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}");

        Matcher matcher = pattern.matcher(wwnString);
        boolean found = false;
        while (matcher.find()) {
            found = true;
        }
        return found;
    }

    /**
     * Validate that the wwn contains hex values and colons (optional)
     * 
     * @param wwnString
     *            string of wwn value
     * @return true if it's fairly valid
     */
    public static boolean isValidEndpoint(String wwnString) {
        if (wwnString != null && wwnString.length() < 16) {
            return false;
        }

        // Just make sure it's long enough and has colons and hex values
        Pattern pattern = Pattern
                .compile("^([0-9a-fA-F:])*");

        Matcher matcher = pattern.matcher(wwnString);
        while (matcher.find()) {
            return true;
        }
        return false;
    }

}

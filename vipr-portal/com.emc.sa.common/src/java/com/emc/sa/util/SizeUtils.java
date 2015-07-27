/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.util;

import java.math.BigDecimal;

public class SizeUtils {

    public static String humanReadableByteCount(double bytes) {
        return humanReadableByteCount(bytes, false);
    }

    public static String humanReadableMegaByteCount(double megabytes) {
        return humanReadableByteCount(megabytes * 1024 * 1024);
    }

    public static String humanReadableByteCount(double bytes, boolean internationalSystemOfUnits) {
        int unit = internationalSystemOfUnits ? 1000 : 1024;
        if (bytes <= 0) {
            return "0"; // default units for zero value
        }
        else if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (internationalSystemOfUnits ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
    
    public static String humanReadableValueOnly(double bytes, boolean internationalSystemOfUnits) {
        int unit = internationalSystemOfUnits ? 1000 : 1024;
        if (bytes <= 0) {
            return "0"; // default units for zero value
        }
        else if (bytes < unit) {
            return Double.toString(bytes);
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        return String.format("%.1f", bytes / Math.pow(unit, exp)); 	
    }    
    
    public static String humanReadableUnits(double bytes, boolean internationalSystemOfUnits) {
        int unit = internationalSystemOfUnits ? 1000 : 1024;
        if (bytes <= 0) {
            return ""; // default units for zero value
        }
        else if (bytes < unit) {
            return "B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (internationalSystemOfUnits ? "kMGTPEZY" : "KMGTPEZY").charAt(exp - 1) + "";
        return String.format("%sB", pre);    	
    }
    
    public static String humanReadableByteCount(BigDecimal bytes, Boolean si) {
        return humanReadableByteCount(bytes.doubleValue(), si);
    }
}

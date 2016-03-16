/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.util;

/**
 * @author burckb
 * 
 */
public class SizeUtil {

    public static final String SIZE_B = "B";
    public static final String SIZE_MB = "MB";
    public static final String SIZE_GB = "GB";
    public static final String SIZE_TB = "TB";
    public static final String SIZE_KB = "KB";

    public static Long translateSize(String size) {
        long sizeVal = 0;
        long multiplier = 1;
        String sizeSubstr;
        if (size.endsWith(SIZE_TB)) {
            multiplier = 1024 * 1024 * 1024 * 1024L;
            sizeSubstr = size.substring(0, size.length() - 2);
        } else if (size.endsWith(SIZE_GB)) {
            multiplier = 1024 * 1024 * 1024L;
            sizeSubstr = size.substring(0, size.length() - 2);
        } else if (size.endsWith(SIZE_MB)) {
            multiplier = 1024 * 1024L;
            sizeSubstr = size.substring(0, size.length() - 2);
        } else if (size.endsWith(SIZE_B)) {
            sizeSubstr = size.substring(0, size.length() - 1);
        } else {
            sizeSubstr = size;
        }
        Double d = Double.valueOf(sizeSubstr.trim()) * multiplier;
        sizeVal = d.longValue();
        return Long.valueOf(sizeVal);
    }

    /**
     * Given size in TB, GB, MB, KB return converted value as bytes
     * 
     * @param size
     *            size in TB, GB, MB, KB, B
     * @return Unit
     */
    public static String getUnit(String size) {
        if (size.endsWith(SIZE_TB)) {
            return SIZE_TB;
        } else if (size.endsWith(SIZE_GB)) {
            return SIZE_GB;
        } else if (size.endsWith(SIZE_MB)) {
            return SIZE_MB;
        } else if (size.endsWith(SIZE_KB)) {
            return SIZE_KB;
        } else {
            return SIZE_B;
        }
    }

    /**
     * Given size in TB, GB, MB, KB return converted value as bytes
     * 
     * @param size
     *            size in TB, GB, MB, KB
     * @param unit
     *            convert from
     * @return converted size in bytes
     */
    public static Long translateSizeToBytes(Long size, String unit) {
        long multiplier = 1;
        String sizeSubstr;
        if (unit.equals(SIZE_TB)) {
            multiplier = 1024 * 1024 * 1024 * 1024L;
        } else if (unit.equals(SIZE_GB)) {
            multiplier = 1024 * 1024 * 1024L;
        } else if (unit.equals(SIZE_MB)) {
            multiplier = 1024 * 1024L;
        } else if (unit.equals(SIZE_KB)) {
            multiplier = 1024L;
        }
        return size * multiplier;
    }

    /**
     * Given size in bytes, return converted value as TB, GB, MB as specified in "to"
     * 
     * @param size
     *            size in bytes
     * @param to
     *            convert to
     * @return converted size
     */
    public static Double translateSize(Long size, String to) {
        if (size == null || size.longValue() == 0) {
            return 0.0;
        }
        long multiplier = 1L;
        if (to.endsWith(SIZE_TB)) {
            multiplier = 1024 * 1024 * 1024 * 1024L;
        } else if (to.endsWith(SIZE_GB)) {
            multiplier = 1024 * 1024 * 1024L;
        } else if (to.endsWith(SIZE_MB)) {
            multiplier = 1024 * 1024L;
        } else if (to.endsWith(SIZE_KB)) {
            multiplier = 1024L;
        }
        return (double) size / (double) multiplier;
    }

    /**
     * Finds the maximum unit that can represent the given value without decimal notation
     * 
     * @param size
     *            size
     * @return suitable unit of storage size
     */
    public static String findUnit(Long size, String unit) {
        long sizeInBytes = translateSizeToBytes(size, unit);
        if (sizeInBytes >= 1073741824 && sizeInBytes % 1073741824 == 0)
            return SIZE_GB;
        else if (sizeInBytes >= 1048576 && sizeInBytes % 1048576 == 0)
            return SIZE_MB;
        else if (sizeInBytes >= 1024 && sizeInBytes % 1024 == 0)
            return SIZE_KB;
        else
            return SIZE_B;
    }
}

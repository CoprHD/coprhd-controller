/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.util;

public class DiskSizeConversionUtils {
    public static final long GB_PER_TB = 1024;
    public static final long MB_PER_GB = 1024;
    public static final long MB_PER_TB = MB_PER_GB * GB_PER_TB;
    public static final long KB_PER_MB = 1024;
    public static final long KB_PER_GB = KB_PER_MB * MB_PER_GB;
    public static final long KB_PER_TB = KB_PER_GB * GB_PER_TB;
    public static final long BYTES_PER_KB = 1024;
    public static final long BYTES_PER_MB = BYTES_PER_KB * KB_PER_MB;
    public static final long BYTES_PER_GB = BYTES_PER_MB * MB_PER_GB;
    public static final long BYTES_PER_TB = BYTES_PER_GB * GB_PER_TB;

    public static long gbToMb(long sizeInGb) {
        return sizeInGb * MB_PER_GB;
    }

    public static long gbToKb(long sizeInGb) {
        return sizeInGb * KB_PER_GB;
    }

    public static long gbToBytes(double sizeInGB) {
        return (long) (sizeInGB * BYTES_PER_GB);
    }

    public static long bytesToGb(long sizeInBytes) {
        return sizeInBytes / BYTES_PER_GB;
    }

    public static long bytesToMb(long sizeInBytes) {
        return sizeInBytes / BYTES_PER_MB;
    }

    public static long mbToBytes(long sizeInMb) {
        return sizeInMb * BYTES_PER_MB;
    }

    public static long mbToGb(long sizeInMb) {
        return sizeInMb / MB_PER_GB;
    }

    public static long mbToKb(long sizeInMb) {
        return sizeInMb * KB_PER_MB;
    }

    public static long tbToBytes(long sizeInTb) {
        return sizeInTb * BYTES_PER_TB;
    }
}

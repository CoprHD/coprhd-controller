/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.util;

import com.emc.storageos.model.block.BlockObjectRestRep;

/**
 * Some array types like HDS do not have a mechanism to get the full actual WWN of volumes.
 * So the ViPR object may contain a partial WWN instead of a full one. This utility will match
 * an actual WWN for a volume from a system against the partial ones in ViPR.
 */
public class VolumeWWNUtils {
    public static final int PARTIAL_WWN_LENGTH = 16;
    public static final int HUS_VM_PARTIAL_WWN_LENGTH = 12;

    public static final int SUFFIX_LENGTH = 4;
    public static final int HUS_PREFIX_LENGTH = 4;
    public static final int PARTIAL_PREFIX_LENGTH = 5;

    public static boolean wwnMatches(String actualWwn, BlockObjectRestRep blockObject) {
        return partialMatch(actualWwn, blockObject.getWwn());
    }

    public static boolean wwnHDSMatches(String actualWwn, BlockObjectRestRep blockObject) {
        String convertedWwn = convertAsciiHDSWwn(actualWwn);

        if (convertedWwn == null) {
            return false; // convert failed
        }

        String useableWwn = convertedWwn;

        // check if we need to created HUS compatible wwn
        if (isHusVmPartialWwn(blockObject.getWwn())) {
            useableWwn = createHusPartialWwn(convertedWwn); // 12 char long
        } else {
            useableWwn = createPartialWwn(convertedWwn); // 16 char long
        }

        return partialMatch(useableWwn, blockObject.getWwn());
    }

    /**
     * Converts ascii encoded wwn returned from powerpath to useable wwn
     * 
     * @param wwn from powerpath
     * @return converted wwn
     */
    private static String convertAsciiHDSWwn(String wwn) {
        if (wwn.length() % 2 != 0) {
            return null; // can't convert if not an even number
        }

        StringBuilder output = new StringBuilder();
        for (int i = 0; i < wwn.length(); i += 2) {
            String str = wwn.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }
        return output.toString();
    }

    /**
     * Create ViPR compatible 12 char wwn
     * 
     * @param wwn to modify
     * @return HUS compatible wwn
     */
    private static String createHusPartialWwn(String wwn) {
        if (wwn.length() < SUFFIX_LENGTH + HUS_PREFIX_LENGTH) {
            return wwn; // return original wwn if length not long enough to make partial
        }
        String prefix = wwn.substring(wwn.length() - (SUFFIX_LENGTH + HUS_PREFIX_LENGTH), wwn.length() - SUFFIX_LENGTH);
        String sufix = wwn.substring(wwn.length() - SUFFIX_LENGTH);

        return (prefix + "0000" + sufix);
    }

    /**
     * Create ViPR compatible 16 char wwn
     * 
     * @param wwn
     * @return HDS compatible 16 char wwn
     */
    private static String createPartialWwn(String wwn) {
        if (wwn.length() < SUFFIX_LENGTH + PARTIAL_PREFIX_LENGTH) {
            return wwn; // return original wwn if length not long enough to make partial
        }
        String prefix = wwn.substring(wwn.length() - (SUFFIX_LENGTH + PARTIAL_PREFIX_LENGTH), wwn.length() - SUFFIX_LENGTH);
        String sufix = wwn.substring(wwn.length() - SUFFIX_LENGTH);

        return ("000" + prefix + "0000" + sufix);
    }

    public static String getPartialWwn(String wwn) {
        if (wwn.length() >= PARTIAL_WWN_LENGTH) {
            return wwn.substring(wwn.length() - PARTIAL_WWN_LENGTH);
        }
        return wwn;
    }

    public static boolean isPartialWwn(String wwn) {
        return (wwn.length() == PARTIAL_WWN_LENGTH);
    }

    public static String getHusVmPartialWwn(String wwn) {
        if (wwn.length() >= HUS_VM_PARTIAL_WWN_LENGTH) {
            return wwn.substring(wwn.length() - HUS_VM_PARTIAL_WWN_LENGTH);
        }
        return wwn;
    }

    public static boolean isHusVmPartialWwn(String wwn) {
        return (wwn.length() == HUS_VM_PARTIAL_WWN_LENGTH);
    }

    /**
     * Partial match of two WWNs. The partial string may match the end or middle characters of the full WWN.
     * 
     * @param actual The actual WWN of a volume. Always 32 characters.
     * @param partial The partial WWN which must match the end or middle part of the actual.
     * @return True if the strings are equal or the partial string is at the end or middle of the actual.
     */
    private static boolean partialMatch(String actual, String partial) {
        if (actual == null || partial == null) {
            return actual == null && partial == null;
        }
        int actualLength = actual.length();
        int partialLength = partial.length();
        if (actualLength == partialLength) {
            return actual.equalsIgnoreCase(partial);
        } else if (actualLength > partialLength) {
            if (actual.toLowerCase().endsWith(partial.toLowerCase())) {
                return true;
            } else if (partialLength == PARTIAL_WWN_LENGTH &&
                    actual.toLowerCase().contains(partial.toLowerCase())) {
                return true;
            }
        }
        // This would only happen is for some reason the partial string was longer
        return false;
    }
}

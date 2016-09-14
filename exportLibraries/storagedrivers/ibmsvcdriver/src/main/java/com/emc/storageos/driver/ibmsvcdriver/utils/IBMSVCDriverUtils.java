/**
 * Copyright (c) 2016 EMC Corporation
 * 
 * All Rights Reserved
 * 
 */
package com.emc.storageos.driver.ibmsvcdriver.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IBMSVCDriverUtils {
    
    private static final Logger _log = LoggerFactory.getLogger(IBMSVCDriverUtils.class);
    
    private static final String KILOBYTECONVERTERVALUE = "1024";
    
    /**
     * convert Bytes to KiloBytes
     * 
     * @param value
     * @return
     */
    public static Long convertBytesToKBytes(String value) {
        if (null == value) return 0L;
        BigDecimal val = new BigDecimal(value);
        BigDecimal kbconverter = new BigDecimal(KILOBYTECONVERTERVALUE);
        BigDecimal result = val.divide(kbconverter, RoundingMode.CEILING);
        // if the passed in Value from Provider is less than 1024 bytes, then by
        // default make it to 1 KB.
        if (result.longValue() == 0)
            return 1L;
        return result.longValue();
    }
    
    /**
     * If the returned value from Provider cannot be accommodated within Long, then make it to 0.
     * as this is not a valid stat.The only possibility to get a high number is ,Provider initializes
     * all stat property values with a default value of uint64. (18444......)
     * Once stats collected, values will then accommodated within Long. 
     * @param value
     * @return
     */
    public static Long getLongValue(String value) {
        try {
            return Long.parseLong(value);
        } catch(Exception e) {
            _log.warn("Cound Not get the LongValue for the valuee {}", value);
        }
        return 0L;
    }
    
    //
    // Parses first group of consecutive digits found into an int.
    //
    public static int extractInt(String str) {
        Matcher matcher = Pattern.compile("\\d+").matcher(str);

        if (!matcher.find())
            throw new NumberFormatException("For input string [" + str + "]");

        return Integer.parseInt(matcher.group());
    }   
    
    //
    // Parses first group of consecutive digits found into an float.
    //
    public static long extractFloat(String str) {
        Matcher matcher = Pattern.compile("\\d+\\.\\d+").matcher(str);

        if (str.equals("0"))
            return 0;
        if (!matcher.find())
            throw new NumberFormatException("For input string [" + str + "]");

        if (str.contains("TB")) {
            return convertTBtoKB(matcher.group());
        } else if (str.contains("GB")) {
            return convertGBtoKB(matcher.group());
         } else if (str.contains("MB")) {
            return convertMBtoKB(matcher.group());
         } else {
             BigDecimal val = new BigDecimal(matcher.group());
             return val.longValue();
         }
    }

    // Adds colons to input WWN string
    public static String formatWWNString(String wwn) {
        return wwn.replaceAll("..(?!$)", "$0:");
    }

    private static long convertTBtoKB(String value) {
        if (null == value) return 0L;
        BigDecimal val = new BigDecimal(value);
        BigDecimal kbconverter = new BigDecimal(KILOBYTECONVERTERVALUE);
        BigDecimal result = val.multiply(kbconverter).multiply(kbconverter).multiply(kbconverter);
        // if the passed in Value from Provider is less than 1024 bytes, then by
        // default make it to 1 KB.
        if (result.longValue() == 0)
            return 1L;
        return result.longValue();
    }

    private static long convertGBtoKB(String value) {
        if (null == value) return 0L;
        BigDecimal val = new BigDecimal(value);
        BigDecimal kbconverter = new BigDecimal(KILOBYTECONVERTERVALUE);
        BigDecimal result = val.multiply(kbconverter).multiply(kbconverter);
        // if the passed in Value from Provider is less than 1024 bytes, then by
        // default make it to 1 KB.
        if (result.longValue() == 0)
            return 1L;
        return result.longValue();
    }    

    private static long convertMBtoKB(String value) {
        if (null == value) return 0L;
        BigDecimal val = new BigDecimal(value);
        BigDecimal kbconverter = new BigDecimal(KILOBYTECONVERTERVALUE);
        BigDecimal result = val.multiply(kbconverter);
        // if the passed in Value from Provider is less than 1024 bytes, then by
        // default make it to 1 KB.
        if (result.longValue() == 0)
            return 1L;
        return result.longValue();
    }

    //
    // Parses first group of consecutive digits found into an float.
    //
    public static long convertGBtoBytes(String str) {
        Matcher matcher = Pattern.compile("\\d+\\.\\d+").matcher(str);

        if (str.equals("0"))
            return 0;
        if (!matcher.find())
            throw new NumberFormatException("For input string [" + str + "]");

        if (str.contains("GB")) {
            return convertGBtoB(matcher.group());
        } else {
            BigDecimal val = new BigDecimal(matcher.group());
            return val.longValue();
        }
    }

    private static long convertGBtoB(String value) {
        if (null == value) return 0L;
        BigDecimal val = new BigDecimal(value);
        BigDecimal kbconverter = new BigDecimal(KILOBYTECONVERTERVALUE);
        BigDecimal result = val.multiply(kbconverter).multiply(kbconverter).multiply(kbconverter);
        // if the passed in Value from Provider is less than 1024 bytes, then by
        // default make it to 1 KB.
        if (result.longValue() == 0)
            return 1L;
        return result.longValue();
    }

    /**
     * <!--[ SanFormat.stringWwn( ba ) ]--> Function to convert a World Wide
     * name from binary format (byte array) to a formatted string.
     *
     * @param baWwn A byte array containing a fibre channel providerNode or port World Wide
     *            Name.
     * @return Returns a formatted String object that displays the world wide
     *         name.
     */
    public static String StringWwn(byte[] baWwn) {

        String sWwn = "";
        if (baWwn != null) {
            for (int i = 0; i < baWwn.length; i++) {
                String s = Integer.toHexString(baWwn[i]);
                if (s.length() > 2)
                    s = s.substring(s.length() - 2, s.length());
                if (s.length() == 1)
                    s = "0" + s;
                sWwn += s;
                if (i < baWwn.length - 1)
                    sWwn += ":";
            }
        }
        return sWwn;
    }
}

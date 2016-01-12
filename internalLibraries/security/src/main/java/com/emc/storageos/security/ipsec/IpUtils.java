/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.ipsec;


import org.slf4j.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class IpUtils {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(IpUtils.class);

    private static Pattern VALID_IPV4_PATTERN = null;
    private static Pattern VALID_IPV6_PATTERN = null;
    private static final String ipv4Pattern = "(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])";
    private static final String ipv6Pattern = "([0-9a-f]{1,4}:){7}([0-9a-f]){1,4}";

    static {
        try {
            VALID_IPV4_PATTERN = Pattern.compile(ipv4Pattern, Pattern.CASE_INSENSITIVE);
            VALID_IPV6_PATTERN = Pattern.compile(ipv6Pattern, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            log.warn("Unable to compile pattern", e);
        }
    }

    /**
     * Determine if the given string is a valid IPv4 address.
     */
    public static boolean isIpv4Address(String ipAddress) {
        Matcher m = VALID_IPV4_PATTERN.matcher(ipAddress);
        return m.matches();
    }

    /**
     * Determine if the given string is a valid IPv6 address.
     */
    public static boolean isIpv6Address(String ipAddress) {
        Matcher m = VALID_IPV6_PATTERN.matcher(ipAddress);
        return m.matches();
    }

    /**
     * decompress input address into a canonical ipv6 address
     * return null if it is not a valid ipv6 address.
     *
     * @param address
     * @return
     */
    public static String decompressIpv6Address(String address){
        if (address == null) {
            return null;
        }

        address=address.trim();
        if (!isIpv6Address(address)) {
            log.warn(address + " is not a valid ipv6 address");
            return null;
        }
        StringBuilder stdForm = new StringBuilder();
        String[] splitted=address.split(":");
        for(String str:splitted){
            if("".equals(str)){
                for(int i=0;i<8-splitted.length;i++){
                    stdForm.append("0000:");
                }
            }else{
                while(str.length()!=4)str="0"+str;
                stdForm.append(str+":");
            }
        }
        return stdForm.substring(0, stdForm.length()-1);
    }
}


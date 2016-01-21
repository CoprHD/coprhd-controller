/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.ipsec;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet6Address;
import java.net.InetAddress;

public class IpUtils {

    private static final Logger log = LoggerFactory.getLogger(IpUtils.class);

    /**
     * decompress input address into a canonical ipv6 address
     *
     * @param address
     * @return
     */

    public static String decompressIpv6Address(String address){
        if (address == null) {
            return null;
        }

        address=address.trim();
        StringBuilder stdForm = new StringBuilder();
        String[] splitted=address.split(":");
        for(String str:splitted){
            if("".equals(str)){
                for(int i=0;i<=8-splitted.length;i++){
                    stdForm.append("0000:");
                }
            }else{
                while(str.length()!=4)str="0"+str;
                stdForm.append(str+":");
            }
        }
        return stdForm.substring(0, stdForm.length()-1);
    }

    /**
     * get local ip address
     *
     * @return local ip string
     */
    public static String getLocalIPAddress() {
        try {
            InetAddress IP = InetAddress.getLocalHost();
            String localIP = IP.getHostAddress();
            if(IP instanceof Inet6Address) {
                localIP = IpUtils.decompressIpv6Address(localIP);
            }
            log.info("IP of my system is : " + localIP);
            return localIP;
        } catch (Exception ex) {
            log.warn("error in getting local ip: " + ex.getMessage());
            return null;
        }
    }
}


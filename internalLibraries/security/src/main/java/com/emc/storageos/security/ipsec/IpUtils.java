/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.ipsec;


public class IpUtils {

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
}


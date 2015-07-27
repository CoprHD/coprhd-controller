/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.kerberos;

import java.util.List;
import java.util.Map;

/**
 * The kerberos config file (krb5.conf) contains all of the configuration for
 * kerberos realms. This class is a builder to generate this file.
 * 
 * @author Chris Dail
 */
public class Krb5ConfBuilder {
    /*
     * Sample file:
     *
     * [libdefaults]
     * 
     * [realms]
     * IWAVETEST.COM = {
     *     kdc = 10.2.1.228
     * }
     * IWAVE.LOCAL = {
     *     kdc = 10.2.1.10
     * }
     * 
     * [domain_realm]
     * .iwavetest.com = IWAVETEST.COM
     * .iwave.local = IWAVE.LOCAL
     */
    
    /**
     * Build a krb5.conf file given a map of realms.
     * 
     * @param domains Maps Domain -> List of KDC's
     * @return Contents of a krb5.conf file
     */
    public static String build(Map<String,List<String>> domains) {
        StringBuilder sb = new StringBuilder();
        sb.append("[libdefaults]\n");
        if (!domains.isEmpty()) {
            String defaultRealm = domains.keySet().iterator().next();
            sb.append("default_realm = ")
                .append(defaultRealm.toUpperCase())
                .append("\n\n");
        }
        sb.append("[realms]\n");
        for (Map.Entry<String,List<String>> entry: domains.entrySet()) {
            sb.append(entry.getKey().toUpperCase())
              .append(" = {\n");

              for (String kdcAddress : entry.getValue()) {
                  sb.append("    kdc = ")
                    .append(kdcAddress)
                    .append("\n");

              }
              sb.append("\n}\n");
        }
        sb.append("\n");
        
        sb.append("[domain_realm]\n");
        for (Map.Entry<String,List<String>> entry: domains.entrySet()) {
            String domain = entry.getKey();
            sb.append('.')
              .append(domain.toLowerCase())
              .append(" = ")
              .append(domain.toUpperCase())
              .append("\n");
        }
        sb.append("\n");
        
        return sb.toString();
    }
}

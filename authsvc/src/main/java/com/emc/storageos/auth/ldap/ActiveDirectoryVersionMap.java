/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.ldap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Active Directory objectVersion to windows server version mapping
 * 
 */
public class ActiveDirectoryVersionMap {

    private static final String UNKOWN_WINDOWS_SERVER_VERSION = "unkown Windows Server version";
    private static final Map<String, String> objectVersionMap;
    static {
        Map<String, String> m = new HashMap<String, String>();
        /*
         * The following diagram maps between the objectVersion attribute value and the Active Directory schema commutability:
         * 13 -> Windows 2000 Server
         * 30 -> Windows Server 2003 RTM, Windows Server 2003 with Service Pack 1, Windows Server 2003 with Service Pack 2
         * 31 -> Windows Server 2003 R2
         * 44 -> Windows Server 2008 RTM
         * 47 -> Windows Server 2008 R2
         * 56 -> Windows Server 2012 RTM
         */
        m.put("13", "Windows 2000 Server");
        m.put("30", "Windows Server 2003 RTM, Windows Server 2003 with Service Pack 1, Windows Server 2003 with Service Pack 2");
        m.put("31", "Windows Server 2003 R2");
        m.put("44", "Windows Server 2008 RTM");
        m.put("47", "Windows Server 2008 R2");
        m.put("56", "Windows Server 2012 RTM");
        objectVersionMap = Collections.unmodifiableMap(m);
    }

    /**
     * Check Active Directory windows server version using objectVersion
     * 
     * @param objVersion
     * @return Active Directory windows server version
     */
    public static String getActiveDirectoryVersion(String objVersion) {
        String version = objectVersionMap.get(objVersion);
        if (version == null) {
            version = UNKOWN_WINDOWS_SERVER_VERSION;
        }
        return version;
    }
}

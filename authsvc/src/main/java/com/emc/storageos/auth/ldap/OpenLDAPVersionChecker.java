/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.auth.ldap;

/**
 * OpenLDAP version checker
 * 
 */
public class OpenLDAPVersionChecker {

    private static final String OPEN_LDAP = "OpenLDAP";
    private static final String OPEN_LDAP_2_0 = "OpenLDAP 2.0";
    private static final String OPEN_LDAP_2_1 = "OpenLDAP 2.1";
    private static final String OPEN_LDAP_2_2 = "OpenLDAP 2.2";
    private static final String OPEN_LDAP_2_3 = "OpenLDAP 2.3";
    private static final String OPEN_LDA_PROOT_DSE = "OpenLDAProotDSE";
    private static final String OID_LANGUAGE_TAG_OPTIONS = "1.3.6.1.4.1.4203.1.5.4";
    private static final String OID_WHOAMI = "1.3.6.1.4.1.4203.1.11.3";
    private static final String OID_Proxy_Authorization_Control = "2.16.840.1.113730.3.4.18";

    /**
     * Check OpenLDAP version using RootDSE
     * 
     * @param rootDSE
     * @return OpenLDAP version, null means unable to check the server's version
     */
    public static String getOpenLDAPVersion(RootDSE rootDSE) {
        // check OpenLDAP
        String serverType = null;
        String[] objectClass = rootDSE.getObjectClass();
        if (objectClass != null && objectClass.length > 0) {
            for (int i = 0; i < objectClass.length; i++) {
                if (OPEN_LDA_PROOT_DSE.equals(objectClass[i])) {
                    String configContext = rootDSE.getConfigContext();
                    boolean typeDetected = false;
                    if (configContext != null) {
                        serverType = OPEN_LDAP_2_3;
                        typeDetected = true;
                    }
                    if (!typeDetected) {
                        String[] supportedControl = rootDSE
                                .getSupportedControl();
                        if (supportedControl != null) {
                            for (int sci = 0; sci < supportedControl.length; sci++) {
                                if (OID_Proxy_Authorization_Control
                                        .equals(supportedControl[sci])) {
                                    serverType = OPEN_LDAP_2_2;
                                    typeDetected = true;
                                }
                            }
                        }
                    }
                    if (!typeDetected) {
                        String[] supportedExtension = rootDSE
                                .getSupportedExtension();
                        if (supportedExtension != null) {
                            for (int sei = 0; sei < supportedExtension.length; sei++) {
                                if (OID_WHOAMI.equals(supportedExtension[sei])) {
                                    serverType = OPEN_LDAP_2_1;
                                    typeDetected = true;
                                }
                            }
                        }
                    }
                    if (!typeDetected) {
                        String[] supportedFeatures = rootDSE
                                .getSupportedFeatures();
                        if (supportedFeatures != null) {
                            for (int sfi = 0; sfi < supportedFeatures.length; sfi++) {
                                if (OID_LANGUAGE_TAG_OPTIONS
                                        .equals(supportedFeatures[sfi])) {
                                    serverType = OPEN_LDAP_2_0;
                                    typeDetected = true;
                                }
                            }
                        }
                    }
                    if (!typeDetected) {
                        serverType = OPEN_LDAP;
                        typeDetected = true;
                    }
                    if (typeDetected) {
                        break;
                    }
                }
            }
        }
        return serverType;
    }

}

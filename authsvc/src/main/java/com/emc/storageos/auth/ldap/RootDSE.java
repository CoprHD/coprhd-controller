/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.ldap;

import java.util.Arrays;


/**
 * RootDSE. Requires LDAP v3 support.
 * 
 */
public class RootDSE {

    private String rootDomainNamingContext;
    private String forestFunctionality;
    private String schemaNamingContext;
    private String[] namingContexts;
    private int[] supportedLDAPVersion;
    private String[] supportedControl;
    private String[] supportedExtension;
    private String[] supportedSASLMechanisms;
    private String vendorName;
    private String vendorVersion;
    private String[] supportedAuthPasswordSchemes;
    private String[] objectClass;
    private String configContext;
    private String[] supportedFeatures;

    public String getRootDomainNamingContext() {
        return rootDomainNamingContext;
    }

    public void setRootDomainNamingContext(String rootDomainNamingContext) {
        this.rootDomainNamingContext = rootDomainNamingContext;
    }

    public String getForestFunctionality() {
        return forestFunctionality;
    }

    public void setForestFunctionality(String forestFunctionality) {
        this.forestFunctionality = forestFunctionality;
    }

    public String getSchemaNamingContext() {
        return schemaNamingContext;
    }

    public void setSchemaNamingContext(String schemaNamingContext) {
        this.schemaNamingContext = schemaNamingContext;
    }

    public String[] getNamingContexts() {
        return namingContexts;
    }

    public void setNamingContexts(String[] namingContexts) {
        this.namingContexts = namingContexts;
    }

    public int[] getSupportedLDAPVersion() {
        return supportedLDAPVersion;
    }

    public void setSupportedLDAPVersion(String[] supportedLDAPVersion) {
        if (supportedLDAPVersion == null) {
            this.supportedLDAPVersion = null;
            return;
        }
        this.supportedLDAPVersion = new int[supportedLDAPVersion.length];
        for (int i = 0; i<supportedLDAPVersion.length; i++) {
            try {
                this.supportedLDAPVersion[i] = Integer.parseInt(supportedLDAPVersion[i]);
            } catch (NumberFormatException ex) {
                // invalid LDAP version string, set version to 0
                this.supportedLDAPVersion[i] = 0;
            }
        }
    }

    public String[] getSupportedControl() {
        return supportedControl;
    }

    public void setSupportedControl(String[] supportedControl) {
        this.supportedControl = supportedControl;
    }

    public String[] getSupportedExtension() {
        return supportedExtension;
    }

    public void setSupportedExtension(String[] supportedExtension) {
        this.supportedExtension = supportedExtension;
    }

    public String[] getSupportedSASLMechanisms() {
        return supportedSASLMechanisms;
    }

    public void setSupportedSASLMechanisms(String[] supportedSASLMechanisms) {
        this.supportedSASLMechanisms = supportedSASLMechanisms;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getVendorVersion() {
        return vendorVersion;
    }

    public void setVendorVersion(String vendorVersion) {
        this.vendorVersion = vendorVersion;
    }

    public String[] getSupportedAuthPasswordSchemes() {
        return supportedAuthPasswordSchemes;
    }

    public void setSupportedAuthPasswordSchemes(
            String[] supportedAuthPasswordSchemes) {
        this.supportedAuthPasswordSchemes = supportedAuthPasswordSchemes;
    }

    public String[] getObjectClass() {
        return objectClass;
    }

    public void setObjectClass(String[] objectClazz) {
        objectClass = objectClazz;
    }

    public String getConfigContext() {
        return configContext;
    }

    public void setConfigContext(String configContext) {
        this.configContext = configContext;
    }

    public String[] getSupportedFeatures() {
        return supportedFeatures;
    }

    public void setSupportedFeatures(String[] supportedFeatures) {
        this.supportedFeatures = supportedFeatures;
    }

    @Override
    public String toString() {
        return String
                .format("RootDSE [rootDomainNamingContext=%s; forestFunctionality=%s; schemaNamingContext=%s; namingContexts=%s; supportedLDAPVersion=%s; supportedControl=%s; supportedExtension=%s; supportedSASLMechanisms=%s; vendorName=%s; vendorVersion=%s; supportedAuthPasswordSchemes=%s; objectClass=%s; configContext=%s; supportedFeatures=%s]",
                        rootDomainNamingContext, forestFunctionality,
                        schemaNamingContext, Arrays.toString(namingContexts),
                        Arrays.toString(supportedLDAPVersion),
                        Arrays.toString(supportedControl),
                        Arrays.toString(supportedExtension),
                        Arrays.toString(supportedSASLMechanisms), vendorName,
                        vendorVersion,
                        Arrays.toString(supportedAuthPasswordSchemes),
                        Arrays.toString(objectClass), configContext,
                        Arrays.toString(supportedFeatures));
    }

}

/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.ldap;

import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.support.AbstractContextMapper;

/**
 * RootDSE ContextMapper
 * 
 */
public class RootDSEContextMapper extends AbstractContextMapper {

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.ldap.core.support.AbstractContextMapper#doMapFromContext(org.springframework.ldap.core.DirContextOperations)
     */
    @Override
    protected Object doMapFromContext(DirContextOperations ctx) {
        RootDSE rootDSE = new RootDSE();

        rootDSE.setNamingContexts(ctx.getStringAttributes("namingContexts"));
        rootDSE.setRootDomainNamingContext(ctx.getStringAttribute("rootDomainNamingContext"));
        rootDSE.setForestFunctionality(ctx.getStringAttribute("forestFunctionality"));
        rootDSE.setSchemaNamingContext(ctx.getStringAttribute("schemaNamingContext"));
        rootDSE.setSupportedLDAPVersion(ctx.getStringAttributes("supportedLDAPVersion"));
        rootDSE.setSupportedAuthPasswordSchemes(ctx.getStringAttributes("supportedAuthPasswordSchemes"));
        rootDSE.setSupportedControl(ctx.getStringAttributes("supportedControl"));
        rootDSE.setSupportedExtension(ctx.getStringAttributes("supportedExtension"));
        rootDSE.setSupportedSASLMechanisms(ctx.getStringAttributes("supportedSASLMechanisms"));
        rootDSE.setVendorName(ctx.getStringAttribute("vendorName"));
        rootDSE.setVendorVersion(ctx.getStringAttribute("vendorVersion"));
        rootDSE.setObjectClass(ctx.getStringAttributes("objectClass"));
        rootDSE.setConfigContext(ctx.getStringAttribute("configContext"));
        rootDSE.setSupportedFeatures(ctx.getStringAttributes("supportedFeatures"));

        return rootDSE;
    }

}

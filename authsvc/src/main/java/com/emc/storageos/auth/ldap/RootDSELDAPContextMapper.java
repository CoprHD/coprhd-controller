/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2011-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.auth.ldap;

import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.support.AbstractContextMapper;

/**
 * Context mapper to find the RootDSE of LDAP.
 *
 */
public class RootDSELDAPContextMapper extends AbstractContextMapper {

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.ldap.core.support.AbstractContextMapper#doMapFromContext(org.springframework.ldap.core.DirContextOperations)
     */
    @Override
    protected Object doMapFromContext(DirContextOperations ctx) {
        RootDSE rootDSE = new RootDSE();

        rootDSE.setNamingContexts(ctx.getStringAttributes("namingContexts"));
        rootDSE.setSchemaNamingContext(ctx.getStringAttribute("subschemaSubentry"));
        rootDSE.setSupportedLDAPVersion(ctx.getStringAttributes("supportedLDAPVersion"));
        rootDSE.setSupportedControl(ctx.getStringAttributes("supportedControl"));
        rootDSE.setSupportedExtension(ctx.getStringAttributes("supportedExtension"));
        rootDSE.setObjectClass(ctx.getStringAttributes("objectClass"));
        rootDSE.setConfigContext(ctx.getStringAttribute("configContext"));
        rootDSE.setSupportedFeatures(ctx.getStringAttributes("supportedFeatures"));

        return rootDSE;
    }

}

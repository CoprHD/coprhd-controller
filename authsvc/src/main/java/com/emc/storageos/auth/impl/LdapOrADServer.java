/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.impl;

import org.springframework.ldap.core.support.LdapContextSource;

import java.util.Arrays;

/**
 * Represent a ldap or AD server used by StorageOSAuthenticationHandler and StorageOSPersonAttributeDao.
 */
public class LdapOrADServer {

    private LdapContextSource contextSource;

    public LdapContextSource getContextSource() {
        return contextSource;
    }

    public void setContextSource(LdapContextSource contextSource) {
        this.contextSource = contextSource;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("IP: " + Arrays.toString(contextSource.getUrls()));
        return sb.toString();
    }
}

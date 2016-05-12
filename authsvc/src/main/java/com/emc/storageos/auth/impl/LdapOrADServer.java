/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.impl;

import org.springframework.ldap.core.support.LdapContextSource;

import java.util.Arrays;

/**
 * Represent a ldap or AD server used by StorageOSAuthenticationHandler and StorageOSPersonAttributeDao.
 * Besides the instance of ContextSource, containing more information about the connection status and how long it's in bad state.
 */
public class LdapOrADServer {

    private LdapContextSource contextSource;
    private boolean isGood;

    public LdapContextSource getContextSource() {
        return contextSource;
    }

    public void setContextSource(LdapContextSource contextSource) {
        this.contextSource = contextSource;
    }

    public boolean isGood() {
        return isGood;
    }

    public void setIsGood(boolean isGood) {
        this.isGood = isGood;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("IP: " + Arrays.toString(contextSource.getUrls()));
        sb.append(", ");
        sb.append("State: " + isGood);
        return sb.toString();
    }
}

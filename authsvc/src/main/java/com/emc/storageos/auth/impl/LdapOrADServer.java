/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.impl;

import org.springframework.ldap.core.support.LdapContextSource;

/**
 * Represent a ldap or AD server used by StorageOSAuthenticationHandler and StorageOSPersonAttributeDao.
 * Besides the instance of ContextSource, containing more information about the connection status and how long it's in bad state.
 */
public class LdapOrADServer {

    private LdapContextSource contextSource;
    private boolean isGood;
    private long badDuration;

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

    public long getBadDuration() {
        return badDuration;
    }

    public void setBadDuration(long badDuration) {
        this.badDuration = badDuration;
    }
}

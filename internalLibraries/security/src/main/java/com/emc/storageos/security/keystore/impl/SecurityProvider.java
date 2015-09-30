/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.keystore.impl;

import java.security.Provider;

/**
 * 
 */
public final class SecurityProvider extends Provider {

    private static final String SECURITY_PROVIDER_NAME = "ViPRSec";
    private static final double SECURITY_PROVIDER_VERSION = 1.0;
    private static final String SECURITY_PROVIDER_INFO = SECURITY_PROVIDER_NAME + " v"
            + SECURITY_PROVIDER_VERSION + ", implementing ViPR distributed keystore";
    public static final String KEYSTORE_TYPE = "DistributedKeyStore";

    /**
     * @param name
     * @param version
     * @param info
     */
    public SecurityProvider() {
        super(SECURITY_PROVIDER_NAME, SECURITY_PROVIDER_VERSION, SECURITY_PROVIDER_INFO);
        put("KeyStore." + KEYSTORE_TYPE,
                "com.emc.storageos.security.keystore.impl.KeystoreEngine");
    }

}

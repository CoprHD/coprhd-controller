/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.keystore;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public interface KeyStoreExporter {

    /**
     * Save Keystore as local file
     * @throws Exception
     */
    public void export() throws KeyStoreException, IOException, NoSuchAlgorithmException,
                                CertificateException, InterruptedException;
}

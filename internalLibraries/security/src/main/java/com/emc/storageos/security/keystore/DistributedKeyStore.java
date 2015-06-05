/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2013-2014 EMC Corporation All Rights Reserved
 * 
 * This software contains the intellectual property of EMC Corporation or is licensed to
 * EMC Corporation from third parties. Use of this software and the intellectual property
 * contained therein is expressly limited to the terms and conditions of the License
 * Agreement under which it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.keystore;

import java.security.KeyStore;
import java.util.Map;

import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.keystore.impl.KeyCertificateEntry;
import com.emc.storageos.security.keystore.impl.TrustedCertificateEntry;

/**
 * 
 */
public interface DistributedKeyStore {

    /**
     * initailizes the keystore with the specified parameters
     * 
     * @param param
     *            the parameters with which to initialize the keystore
     * @throws SecurityException
     *             if bad parameters are given
     */
    public void init(KeyStore.LoadStoreParameter param) throws SecurityException;

    /**
     * gets the stored trusted certificates from persistence, if any. else, returns an
     * empty map.
     * 
     * @return the stored trusted certificates
     * @throws SecurityException
     *             when reading the certifiactes have failed
     */
    public Map<String, TrustedCertificateEntry> getTrustedCertificates()
            throws SecurityException;

    /**
     * persists the trusted certificates
     * 
     * @param trustedCerts
     *            the certificates to persist
     * @throws SecurityException
     *             if persistence of the certificates have failed
     */
    public void setTrustedCertificates(Map<String, TrustedCertificateEntry> trustedCerts)
            throws SecurityException;

    /**
     * get all root certificates who are from well known CA preinstalled with ViPR from keystore
     * @return the map of ca certificates
     */
    public Map<String, TrustedCertificateEntry> getCACertificates();

    /**
     * set all root certificates to key store.
     * @param trustedCerts
     */
    public void setCACertificates(Map<String, TrustedCertificateEntry> trustedCerts);

    /**
     * remove a CA certificate
     * @param alias
     */
    public void removeCACertificate(String alias);

    /**
     * adds a trusted certificate to the list of persisted certificates
     * 
     * @param alias
     *            the alias under which to persist the certificate
     * @param cert
     *            the trusted certificate to persist
     * @throws SecurityException
     *             if persistence of the certificate has failed
     */
    public void addTrustedCertificate(String alias, TrustedCertificateEntry cert)
            throws SecurityException;

    /**
     * removes the specified trusted certificate from persistence. does nothing if a
     * certificate with the specified alias does not exist
     * 
     * @param alias
     *            the alias of the certificate to remove
     * @throws SecurityException
     *             if failed to remove the specified certificate
     */
    public void removeTrustedCertificate(String alias) throws SecurityException;

    /**
     * gets the persisted KeyCertificateEntry. returns null if it's not found
     * 
     * @return KeyCertificateEntry
     * @throws SecurityException
     *             if failed to retrieve the KeyCertificateEntry
     */
    public KeyCertificateEntry getKeyCertificatePair() throws SecurityException;

    /**
     * sets the KeyCertificateEntry
     * 
     * @param entry
     *            the entry to set
     * @throws SecurityException
     *             if persistence of the entry has failed
     */
    public void setKeyCertificatePair(KeyCertificateEntry entry) throws SecurityException;

    TrustedCertificateEntry getUserAddedCert(String alias);

    TrustedCertificateEntry getViprAddedCert(String alias);

    boolean containsUserAddedCerts(String alias);

    boolean containsViprSuppliedCerts(String alias);
}

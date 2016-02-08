/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.keystore.impl;

import java.io.IOException;
import java.security.KeyStore.LoadStoreParameter;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.lf5.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.keystore.DistributedKeyStore;
import com.emc.storageos.services.util.AlertsLogger;

import org.apache.curator.framework.recipes.locks.InterProcessLock;

/**
 * Implementation for ZooKeeper based keystore
 */
public class DistributedKeyStoreImpl implements DistributedKeyStore {

    static final String KEY_CERTIFICATE_PAIR_LOCK = "keyCertificatePairLock";
    static final String KEY_CERTIFICATE_PAIR_CONFIG_KIND = Constants.KEY_CERTIFICATE_PAIR_CONFIG_KIND;
    static final String KEY_CERTIFICATE_PAIR_ID = "keyCertificatePairId";
    static final String KEY_CERTIFICATE_PAIR_KEY = "keyCertificatePairEntry";
    static final String IS_SELF_GENERATED_KEY = "isSelfGeneratedKeyCertificatePairEntry";

    public static final String TRUSTED_CERTIFICATES_LOCK = "trustedCertificatesLock";

    public static final String TRUSTED_CERTIFICATES_CONFIG_KIND = "trustedCertificatesConfig";
    private static final String LAST_CERTIFICATE_ALERT_ID = "lastCertificateAlertId";
    private static final String LAST_CERTIFICATE_ALERT_KEY = "keyCertificatePairEntry";
    private static final String TRUSTED_CERTIFICATES_CONFIG_KEY = "trustedCertificatesKey";

    public static final String CA_CERTIFICATES_CONFIG_KIND = "caCerts";
    static final String CA_CERTIFICATES_CONFIG_ID = "caCertsConfig";
    static final String CA_CERTIFICATES_CONFIG_KEY_VERSION = "caCertsVersion";

    public static final String UPDATE_LOG = "updateLog";
    public static final String UPDATE_TIME = "updateTime";

    private static Logger log = LoggerFactory.getLogger(DistributedKeyStoreImpl.class);
    private static AlertsLogger alertsLog = AlertsLogger.getAlertsLogger();
    private CoordinatorConfigStoringHelper coordConfigStoringHelper;
    private KeyCertificatePairGenerator generator;

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.security.keystore.DistributedKeyStore#init(java.security.KeyStore.LoadStoreParameter)
     */
    @Override
    public void init(LoadStoreParameter param) throws SecurityException {
        if (param instanceof DistributedLoadKeyStoreParam) {
            DistributedLoadKeyStoreParam zkConnectionInfo =
                    (DistributedLoadKeyStoreParam) param;
            CoordinatorClient coordinator = zkConnectionInfo.getCoordinator();
            coordConfigStoringHelper = new CoordinatorConfigStoringHelper(coordinator);
            generator = new KeyCertificatePairGenerator();
            generator
                    .setKeyCertificateAlgorithmValuesHolder(new KeyCertificateAlgorithmValuesHolder(
                            coordinator));
        } else {
            throw SecurityException.fatals
                    .failedToInitializedKeystoreNeedDistKeystoreParams();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.security.keystore.DistributedKeyStore#getTrustedCertificates()
     */
    @Override
    public Map<String, TrustedCertificateEntry> getTrustedCertificates()
            throws SecurityException {
        try {
            return coordConfigStoringHelper.readAllConfigs(TRUSTED_CERTIFICATES_CONFIG_KIND,
                    TRUSTED_CERTIFICATES_CONFIG_KEY);
        } catch (IOException | ClassNotFoundException e) {
            throw SecurityException.fatals.failedToReadTrustedCertificates(e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.security.keystore.DistributedKeyStore#setTrustedCertificates(java.util.Map)
     */
    @Override
    public synchronized void setTrustedCertificates(
            Map<String, TrustedCertificateEntry> trustedCerts)
            throws SecurityException {
        try {
            coordConfigStoringHelper.removeAllConfigOfKInd(TRUSTED_CERTIFICATES_LOCK,
                    TRUSTED_CERTIFICATES_CONFIG_KIND);
        } catch (Exception e) {
            // it doesn't really matter if this failed
            log.warn(e.getMessage(), e);
        }
        if (!CollectionUtils.isEmpty(trustedCerts)) {
            for (Entry<String, TrustedCertificateEntry> entry : trustedCerts.entrySet()) {
                try {
                    log.info("adding the following as trusted certificate:"
                            + entry.getValue().getCertificate().toString());
                    coordConfigStoringHelper.createOrUpdateConfig(entry.getValue(),
                            TRUSTED_CERTIFICATES_LOCK,
                            TRUSTED_CERTIFICATES_CONFIG_KIND, entry.getKey(),
                            TRUSTED_CERTIFICATES_CONFIG_KEY);
                } catch (Exception e) {
                    throw SecurityException.fatals.failedToUpdateTrustedCertificates(e);
                }
            }
        }
    }

    @Override
    public Map<String, TrustedCertificateEntry> getCACertificates() {
        try {
            return coordConfigStoringHelper.readAllConfigs(CA_CERTIFICATES_CONFIG_KIND,
                    TRUSTED_CERTIFICATES_CONFIG_KEY);
        } catch (IOException | ClassNotFoundException e) {
            throw SecurityException.fatals.failedToReadTrustedCertificates(e);
        }
    }

    @Override
    public void setCACertificates(Map<String, TrustedCertificateEntry> trustedCerts) {

        // clean all before setting
        try {
            coordConfigStoringHelper.removeAllConfigOfKInd(TRUSTED_CERTIFICATES_LOCK,
                    CA_CERTIFICATES_CONFIG_KIND);
        } catch (Exception e) {
            // it doesn't really matter if this failed
            log.warn(e.getMessage(), e);
        }

        if (CollectionUtils.isEmpty(trustedCerts)) {
            return;
        }

        // set
        for (Entry<String, TrustedCertificateEntry> entry : trustedCerts.entrySet()) {
            try {
                log.info("adding the following as trusted certificate:"
                        + entry.getValue().getCertificate().toString());
                coordConfigStoringHelper.createOrUpdateConfig(entry.getValue(),
                        TRUSTED_CERTIFICATES_LOCK,
                        CA_CERTIFICATES_CONFIG_KIND, entry.getKey(),
                        TRUSTED_CERTIFICATES_CONFIG_KEY);
            } catch (Exception e) {
                throw SecurityException.fatals.failedToUpdateTrustedCertificates(e);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.security.keystore.DistributedKeyStore#removeTrustedCertificate(java.lang.String)
     */
    @Override
    public synchronized void removeCACertificate(String alias)
            throws SecurityException {
        try {
            log.info("removing the trusted CA certificate whose alias is " + alias);
            coordConfigStoringHelper.removeConfig(TRUSTED_CERTIFICATES_LOCK,
                    CA_CERTIFICATES_CONFIG_KIND,
                    alias);
        } catch (Exception e) {
            throw SecurityException.fatals.failedToUpdateTrustedCertificates(e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.security.keystore.DistributedKeyStore#addTrustedCertificate(java.lang.String,
     * com.emc.storageos.security.keystore.TrustedCertificateEntry)
     */
    @Override
    public synchronized void addTrustedCertificate(String alias,
            TrustedCertificateEntry cert)
            throws SecurityException {
        try {
            log.info("adding the following trusted certificate under alias " + alias
                    + ": " + cert.getCertificate().toString());
            coordConfigStoringHelper.createOrUpdateConfig(cert, TRUSTED_CERTIFICATES_LOCK,
                    TRUSTED_CERTIFICATES_CONFIG_KIND, alias,
                    TRUSTED_CERTIFICATES_CONFIG_KEY);
        } catch (Exception e) {
            throw SecurityException.fatals.failedToUpdateTrustedCertificates(e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.security.keystore.DistributedKeyStore#removeTrustedCertificate(java.lang.String)
     */
    @Override
    public synchronized void removeTrustedCertificate(String alias)
            throws SecurityException {
        try {
            log.info("removing the trusted certificate whose alias is " + alias);
            coordConfigStoringHelper.removeConfig(TRUSTED_CERTIFICATES_LOCK,
                    TRUSTED_CERTIFICATES_CONFIG_KIND,
                    alias);
        } catch (Exception e) {
            throw SecurityException.fatals.failedToUpdateTrustedCertificates(e);
        }
    }

    /**
     * @see com.emc.storageos.security.keystore.DistributedKeyStore#getKeyCertificatePair()
     */
    @Override
    public KeyCertificateEntry getKeyCertificatePair() throws SecurityException {
        log.info("Retrieving ViPR certificate");
        KeyCertificateEntry entryToReturn;
        try {
            entryToReturn = readKeyCertificateEntry();
            if (entryToReturn == null) {
                entryToReturn = setupKeyCertificatePair();
            } else {
                entryToReturn = checkKeyCertificatePair(entryToReturn);
            }
            log.info("Retrieved ViPR certificate successfully");
        } catch (IOException | ClassNotFoundException e) {
            throw SecurityException.fatals.failedToReadKeyCertificateEntry(e);
        } 

        return entryToReturn;
    }

    private InterProcessLock acquireKeyCertificatePairLock() {
        InterProcessLock lock;
        try {
            lock = coordConfigStoringHelper.acquireLock(KEY_CERTIFICATE_PAIR_LOCK);
        } catch (Exception e) {
            throw SecurityException.fatals.failedToGetKeyCertificate();
        }
        if (lock == null) {
            throw SecurityException.fatals.failedToGetKeyCertificate();
        }
        return lock;
    }
    
    private KeyCertificateEntry setupKeyCertificatePair() throws IOException, ClassNotFoundException{
        InterProcessLock lock = null;
        try {
            lock = acquireKeyCertificatePairLock();
            // re-read the key/cert pair after lock acquired. avoid the case another concurrent thread may have done that 
            KeyCertificateEntry entryToReturn = readKeyCertificateEntry(); 
            if (entryToReturn == null) {
                log.info("ViPR certificate not found");
                entryToReturn = generator.tryGetV1Cert();
                if (entryToReturn != null) {
                    KeyStoreUtil.setSelfGeneratedCertificate(coordConfigStoringHelper,
                            Boolean.FALSE);
                    entryToReturn.setCreationDate(new Date());
                    setKeyCertificatePair(entryToReturn);
                } else {
                    log.info("Generating new certificate");
                    entryToReturn = generateNewKeyCertificatePair();
                }
            }
            return entryToReturn;
        } finally {
            coordConfigStoringHelper.releaseLock(lock);
        }
    }
    
    private KeyCertificateEntry checkKeyCertificatePair(KeyCertificateEntry entry) throws IOException, ClassNotFoundException {
        InterProcessLock lock = null;
        X509Certificate cert = (X509Certificate) entry.getCertificateChain()[0];
        if (KeyStoreUtil.isSelfGeneratedCertificate(coordConfigStoringHelper)
                && !generator.isCertificateIPsCorrect(cert)) {
            try {
                lock = acquireKeyCertificatePairLock();
                // re-read the key/cert pair after lock acquired. avoid the case another concurrent thread may have done that
                entry = readKeyCertificateEntry();
                if (!generator.isCertificateIPsCorrect(cert)) {
                    log.info("ViPR certificate is self generated and has illegal IPs. Generating a new one...");
                    entry = generateNewKeyCertificatePair();
                }
            } finally {
                coordConfigStoringHelper.releaseLock(lock);
            }
        }
        checkCertificateDateValidity(cert);
        return entry;
    }
    
    private KeyCertificateEntry readKeyCertificateEntry() throws IOException, ClassNotFoundException{
        KeyCertificateEntry entryToReturn =
                coordConfigStoringHelper.readConfig(coordConfigStoringHelper.getSiteId(), KEY_CERTIFICATE_PAIR_CONFIG_KIND,
                        KEY_CERTIFICATE_PAIR_ID,
                        KEY_CERTIFICATE_PAIR_KEY);
        if (entryToReturn == null) {
            log.info("Certificate not found from site specific area. Try global area");
            entryToReturn =
                    coordConfigStoringHelper.readConfig(KEY_CERTIFICATE_PAIR_CONFIG_KIND,
                            KEY_CERTIFICATE_PAIR_ID,
                            KEY_CERTIFICATE_PAIR_KEY);
            if (entryToReturn != null) {
                InterProcessLock lock = null;
                try {
                    lock = acquireKeyCertificatePairLock();
                    // re-read from global area after acquiring the lock
                    entryToReturn =
                            coordConfigStoringHelper.readConfig(KEY_CERTIFICATE_PAIR_CONFIG_KIND,
                                    KEY_CERTIFICATE_PAIR_ID,
                                    KEY_CERTIFICATE_PAIR_KEY);
                    if (entryToReturn != null) {
                        String siteId = coordConfigStoringHelper.getSiteId();
                        log.info("Found certificate from global area. Moving to site specific area");
                        coordConfigStoringHelper.createOrUpdateConfig(entryToReturn, KEY_CERTIFICATE_PAIR_LOCK,
                            siteId, KEY_CERTIFICATE_PAIR_CONFIG_KIND, KEY_CERTIFICATE_PAIR_ID,
                            KEY_CERTIFICATE_PAIR_KEY);
                        Boolean isSelfSigned = coordConfigStoringHelper.readConfig(
                                DistributedKeyStoreImpl.KEY_CERTIFICATE_PAIR_CONFIG_KIND,
                                DistributedKeyStoreImpl.KEY_CERTIFICATE_PAIR_ID,
                                DistributedKeyStoreImpl.IS_SELF_GENERATED_KEY);
                        KeyStoreUtil.setSelfGeneratedCertificate(coordConfigStoringHelper, isSelfSigned);
                        coordConfigStoringHelper.removeConfig(KEY_CERTIFICATE_PAIR_LOCK, KEY_CERTIFICATE_PAIR_CONFIG_KIND, KEY_CERTIFICATE_PAIR_ID);
                    }
                } catch (Exception ex) {
                    log.error("Failed to move key certificate pair to site specific area", ex);
                } finally {
                    coordConfigStoringHelper.releaseLock(lock);
                }
                
            }
        }
        return entryToReturn;
    }
    /**
     * Generates a new key certificate pair
     * 
     * @return
     */
    private KeyCertificateEntry generateNewKeyCertificatePair() {
        KeyCertificateEntry entryToReturn;
        entryToReturn = generator.generateKeyCertificatePair();
        entryToReturn.setCreationDate(new Date());
        setKeyCertificatePair(entryToReturn);
        KeyStoreUtil.setSelfGeneratedCertificate(coordConfigStoringHelper, Boolean.TRUE);
        return entryToReturn;
    }

    /**
     * checks if the certificate is about to expire, and alerts in case it is
     * 
     * @param certificate
     *            the certificate to check
     */
    private void checkCertificateDateValidity(X509Certificate certificate) {
        final String CERTIFICATE_EXPIRED_MESSAGE =
                "ViPR's certificate has expired. Please set a new one, or post a regenerate request";
        final String CERTIFICATE_WILL_EXPIRE_MESSAGE_FORMAT =
                "ViPR's certificate will expire within %d %s. Please make arrangements to set a new certificate or to post a regenerate request";
        Date lastCertificateAlert = null;
        try {
            lastCertificateAlert =
                    coordConfigStoringHelper.readConfig(coordConfigStoringHelper.getSiteId(), KEY_CERTIFICATE_PAIR_CONFIG_KIND,
                            LAST_CERTIFICATE_ALERT_ID, LAST_CERTIFICATE_ALERT_KEY);
        } catch (Exception e) {
            // don't really care about the exception here
            log.warn(e.getMessage(), e);
        }
        Date notAfter = DateUtils.truncate(certificate.getNotAfter(), Calendar.DATE);
        Date today = DateUtils.truncate(new Date(), Calendar.DATE);
        Date nextWeek = DateUtils.addWeeks(today, 1);
        Date nextMonth = DateUtils.addMonths(today, 1);
        Date next3Months = DateUtils.addMonths(today, 3);
        Date next6Months = DateUtils.addMonths(today, 6);
        if (lastCertificateAlert == null || DateUtils.truncatedCompareTo(lastCertificateAlert, today, Calendar.DATE) < 0) {
            boolean logAlert = false;
            String messageToLog = CERTIFICATE_WILL_EXPIRE_MESSAGE_FORMAT;
            int timeAmount = 0;
            String timeType = null;
            LogLevel logLevel = LogLevel.WARN;
            if (notAfter.before(today)) {
                logLevel = LogLevel.FATAL;
                messageToLog = CERTIFICATE_EXPIRED_MESSAGE;
                logAlert = true;
            } else if (DateUtils.isSameDay(notAfter, today)) {
                timeType = "days";
                logLevel = LogLevel.ERROR;
                logAlert = true;
            } else if (notAfter.before(nextWeek)) {
                timeAmount = 1;
                timeType = "week";
                logAlert = true;
            } else if (notAfter.before(nextMonth)) {
                timeAmount = 1;
                timeType = "month";
                logAlert = true;
            } else if (notAfter.before(next3Months)) {
                timeAmount = 3;
                timeType = "months";
                logAlert = true;
            } else if (notAfter.before(next6Months)) {
                timeAmount = 6;
                timeType = "months";
                logAlert = true;
            }
            if (logAlert) {
                logAlert(messageToLog, timeAmount, timeType, logLevel);
                try {
                    coordConfigStoringHelper.createOrUpdateConfig(today, KEY_CERTIFICATE_PAIR_LOCK,
                            coordConfigStoringHelper.getSiteId(), KEY_CERTIFICATE_PAIR_CONFIG_KIND, LAST_CERTIFICATE_ALERT_ID,
                            LAST_CERTIFICATE_ALERT_KEY);
                } catch (Exception e) {
                    log.error(
                            "Could not set the time of last alert about certificate expiry",
                            e);
                }
            }
        }
    }

    /**
     * 
     * @param messageFormatToLog
     * @param timeAmount
     * @param timeType
     * @param logLevel
     */
    private void logAlert(String messageFormatToLog, int timeAmount, String timeType,
            LogLevel logLevel) {
        String messageToLog = String.format(messageFormatToLog, timeAmount, timeType);
        if (logLevel == LogLevel.FATAL) {
            alertsLog.fatal(messageToLog);
            log.error(messageToLog);
        } else if (logLevel == LogLevel.ERROR) {
            alertsLog.error(messageToLog);
            log.error(messageToLog);
        } else if (logLevel == LogLevel.WARN) {
            alertsLog.warn(messageToLog);
            log.warn(messageToLog);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.security.keystore.DistributedKeyStore#addTrustedCertificate(com
     * .emc.storageos.security.keystore.impl.KeyCertificateEntry)
     */
    @Override
    public void setKeyCertificatePair(KeyCertificateEntry entry)
            throws SecurityException {
        try {
            if (entry != null) {
                log.info("Setting ViPR's key and certificate chain. New certificate is: "
                        + entry.getCertificateChain()[0]);
            }
            coordConfigStoringHelper.createOrUpdateConfig(entry, KEY_CERTIFICATE_PAIR_LOCK,
                    coordConfigStoringHelper.getSiteId(), KEY_CERTIFICATE_PAIR_CONFIG_KIND, KEY_CERTIFICATE_PAIR_ID,
                    KEY_CERTIFICATE_PAIR_KEY);
        } catch (Exception e) {
            throw SecurityException.fatals.failedToUpdateKeyCertificateEntry(e);
        }
        try {
            coordConfigStoringHelper.removeConfig(KEY_CERTIFICATE_PAIR_LOCK,
                    KEY_CERTIFICATE_PAIR_CONFIG_KIND, LAST_CERTIFICATE_ALERT_ID);
        } catch (Exception e) {
            // don't really care if this fails
            log.error("Could not set the time of last alert about certificate expiry", e);
        }
    }

    @Override
    public TrustedCertificateEntry getUserAddedCert(String alias) {

        try {
            return coordConfigStoringHelper.readConfig(TRUSTED_CERTIFICATES_CONFIG_KIND, alias,
                    TRUSTED_CERTIFICATES_CONFIG_KEY);
        } catch (IOException | ClassNotFoundException e) {
            throw SecurityException.fatals.failedToReadTrustedCertificates(e);
        }
    }

    @Override
    public TrustedCertificateEntry getViprAddedCert(String alias) {

        try {
            return coordConfigStoringHelper.readConfig(CA_CERTIFICATES_CONFIG_KIND, alias,
                    TRUSTED_CERTIFICATES_CONFIG_KEY);
        } catch (IOException | ClassNotFoundException e) {
            throw SecurityException.fatals.failedToReadTrustedCertificates(e);
        }
    }

    @Override
    public boolean containsUserAddedCerts(String alias) {
        try {
            if (null == coordConfigStoringHelper.readConfig(TRUSTED_CERTIFICATES_CONFIG_KIND, alias,
                    TRUSTED_CERTIFICATES_CONFIG_KEY)) {
                return false;
            } else {
                return true;
            }
        } catch (IOException | ClassNotFoundException e) {
            throw SecurityException.fatals.failedToReadTrustedCertificates(e);
        }
    }

    @Override
    public boolean containsViprSuppliedCerts(String alias) {

        try {
            if (null == coordConfigStoringHelper.readConfig(CA_CERTIFICATES_CONFIG_KIND, alias,
                    TRUSTED_CERTIFICATES_CONFIG_KEY)) {
                return false;
            } else {
                return true;
            }
        } catch (IOException | ClassNotFoundException e) {
            throw SecurityException.fatals.failedToReadTrustedCertificates(e);
        }
    }
}

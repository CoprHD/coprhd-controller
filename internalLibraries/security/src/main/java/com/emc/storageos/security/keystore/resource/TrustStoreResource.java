/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.keystore.resource;

import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;

import com.emc.storageos.security.keystore.impl.*;
import com.emc.vipr.model.keystore.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.keystore.impl.CoordinatorConfigStoringHelper;
import com.emc.storageos.security.keystore.impl.KeyCertificatePairGenerator;
import com.emc.storageos.security.keystore.impl.KeyStoreUtil;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * A resource for truststore related requests
 */
@Path("/vdc/truststore")
public class TrustStoreResource {

    private static final String EVENT_SERVICE_TYPE = "Truststore";

    private static Logger log = LoggerFactory.getLogger(TrustStoreResource.class);

    @Context
    protected SecurityContext sc;

    @Autowired
    protected AuditLogManager auditMgr;

    private CoordinatorClient coordinator;
    private CoordinatorConfigStoringHelper coordConfigStoringHelper;

    private KeyStore viprKeyStore;

    protected CertificateVersionHelper certificateVersionHelper;

    public void setCertificateVersionHelper(
            CertificateVersionHelper certificateVersionHelper) {
        this.certificateVersionHelper = certificateVersionHelper;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public void setCoordConfigStoringHelper(CoordinatorConfigStoringHelper coordConfigStoringHelper) {
        this.coordConfigStoringHelper = coordConfigStoringHelper;
    }

    /**
     * Get Trusted Certificates
     * 
     * @brief List certificates
     * @return
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    public TrustedCertificates getTrustedCertificates() {

        List<TrustedCertificate> trustedCertsList = new ArrayList<TrustedCertificate>();

        try {
            for (String alias : Collections.list(getKeyStore().aliases())) {
                log.debug("get alias {}", alias);
                if (getKeyStore().isCertificateEntry(alias)) {

                    boolean userSupplied = KeystoreEngine.isUserSuppliedCerts(alias);

                    Certificate cert = getKeyStore().getCertificate(alias);

                    TrustedCertificate tc = new TrustedCertificate(
                            KeyCertificatePairGenerator.getCertificateAsString(cert), userSupplied);

                    trustedCertsList.add(tc);
                }
            }
        } catch (KeyStoreException e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e);
        } catch (CertificateEncodingException e) {
            log.error(e.getMessage(), e);
            throw SecurityException.fatals.couldNotParseCertificateToString(e);
        }

        TrustedCertificates certs = new TrustedCertificates();
        certs.setTrustedCertificates(trustedCertsList);

        return certs;
    }

    /**
     * Add Or Remove Trusted Certificate
     * 
     * @param changes
     * @brief Add or delete certificates
     * @return
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    public TrustedCertificates addOrRemoveTrustedCertificate(
            TrustedCertificateChanges changes) {

        // to hold all kinds of certs in request to support add/remove in batch, and to support partial success as well
        class UpdateResult {
            int nAdded = 0;
            int nRemoved = 0;
            List<Integer> failToParse = new ArrayList<>();
            List<Integer> expired = new ArrayList<>();
            List<Integer> notExisted = new ArrayList<>();

            public boolean hasAnyFailure() {
                return ( failToParse.isEmpty() && expired.isEmpty() && notExisted.isEmpty() ) ? false:true;
            }

            public boolean hasSuccess() {
                return ( nAdded == 0 && nRemoved == 0 ) ? false:true;
            }
        }

        if (!coordinator.isClusterUpgradable()) {
            throw SecurityException.retryables.updatingKeystoreWhileClusterIsUnstable();
        }

        KeyStore keystore = getKeyStore();
        UpdateResult result = new UpdateResult();

        List<String> certsToAdd = changes.getAdd();

        if (certsToAdd != null) {
            for (int i = 0; i < certsToAdd.size(); i++) {
                String certString = certsToAdd.get(i);
                try {
                    Certificate cert = KeyCertificatePairGenerator.getCertificateFromString(certString);
                    // if we were able to parse the cert, and there wasn't more that 1
                    // cert in this certificate entry
                    if (cert != null
                            && StringUtils.countMatches(certString, KeyCertificatePairGenerator.PEM_BEGIN_CERT) == 1) {
                        String alias = DigestUtils.sha512Hex(cert.getEncoded());
                        if (!keystore.containsAlias(alias)) {
                            X509Certificate x509cert = (X509Certificate) cert;
                            Date now = new Date();
                            if (x509cert.getNotAfter().before(now)) {
                                log.warn("The following certificate has expired: {}", certString);
                                result.expired.add(i+1); // start from 1 for easier understanding for user
                            } else if (now.before(x509cert.getNotBefore())) {
                                log.warn("The following certificate is not yet valid: {} ", certString);
                                result.expired.add(i+1);
                            } else { // good one
                                keystore.setCertificateEntry(alias, cert);
                                result.nAdded ++;
                            }
                        }
                    } else {
                        result.failToParse.add(i+1);
                    }
                } catch (KeyStoreException e) {
                    throw new IllegalStateException("keystore is not initialized", e);
                } catch (CertificateException e) {
                    log.debug(e.getMessage(), e);
                    result.failToParse.add(i+1);
                }
            }
        }

        List<String> certsToRemove = changes.getRemove();
        if (certsToRemove != null) {
            for (int i = 0; i < certsToRemove.size(); i++) {
                String certString = certsToRemove.get(i);
                Certificate cert;
                try {
                    cert = KeyCertificatePairGenerator.getCertificateFromString(certString);
                    if (cert != null
                            && StringUtils.countMatches(certString, KeyCertificatePairGenerator.PEM_BEGIN_CERT) == 1) {
                        keystore.deleteEntry(DigestUtils.sha512Hex(cert.getEncoded()));
                        result.nRemoved ++;
                    } else {
                        result.failToParse.add(i+1);
                    }
                } catch (CertificateException | KeyStoreException e) {
                    log.warn("the following certificate could not be deleted: {}", certString, e);
                    result.notExisted.add(i+1);
                }

            }
        }

        // set AcceptAll to No if any certificate is added.
        if ( (result.nAdded > 0) && getTruststoreSettings().isAcceptAllCertificates()) {
            TruststoreSettingsChanges settingsChanges = new TruststoreSettingsChanges();
            settingsChanges.setAcceptAllCertificates(false);
            changeSettingInternal(settingsChanges);
        }

        if (result.hasSuccess()) {
            // To update the zk and then make service get notified on change.
            recordTrustChangeIfAny();
            auditTruststore(OperationTypeEnum.UPDATE_TRUSTED_CERTIFICATES, changes);
        }

        if (result.hasAnyFailure()) {
            int nAdd = (certsToAdd == null) ? 0 : certsToAdd.size();
            int nRemove = (certsToRemove == null) ? 0 : certsToRemove.size();
            int nFailToAdd = result.failToParse.size() + result.expired.size();
            int nFailToRemove = result.notExisted.size();
            throw APIException.badRequests.trustStoreUpdatePartialSuccess(nAdd, nFailToAdd, result.failToParse, result.expired,
                    nRemove, nFailToRemove, result.notExisted);
        }

        // All good
        return getTrustedCertificates();
    }

    /**
     * set a flag in zk if any change happened.
     */
    private void recordTrustChangeIfAny() {
        try {
            coordConfigStoringHelper.createOrUpdateConfig(System.nanoTime(),
                    DistributedKeyStoreImpl.TRUSTED_CERTIFICATES_LOCK,
                    DistributedKeyStoreImpl.TRUSTED_CERTIFICATES_CONFIG_KIND,
                    DistributedKeyStoreImpl.UPDATE_LOG,
                    DistributedKeyStoreImpl.UPDATE_TIME);
        } catch (Exception e) {
            throw SecurityException.fatals.failToNotifyChange(e);
        }
    }

    /**
     * Get Truststore Settings
     * 
     * @brief Show truststore
     * @return
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    @Path("/settings")
    public TruststoreSettings getTruststoreSettings() {
        TruststoreSettings settings = new TruststoreSettings();
        settings.setAcceptAllCertificates(KeyStoreUtil.getAcceptAllCerts(coordConfigStoringHelper));
        return settings;
    }

    /**
     * Update Truststore Settings
     * 
     * @param changes
     * @brief Update truststore	
     * @return
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    @Path("/settings")
    public TruststoreSettings updateTruststoreSettings(TruststoreSettingsChanges changes) {
        changeSettingInternal(changes);

        /*
         * todo temp comments out
         * if (!certificateVersionHelper.updateCertificateVersion()) {
         * throw SecurityException.fatals.failedRebootAfterKeystoreChange();
         * }
         */
        return getTruststoreSettings();
    }

    /**
     * @param changes
     * @return
     */
    private void changeSettingInternal(TruststoreSettingsChanges changes) {
        if (!coordinator.isClusterUpgradable()) {
            throw SecurityException.retryables.updatingKeystoreWhileClusterIsUnstable();
        }
        TruststoreSettings currentSettings = getTruststoreSettings();

        // if current and changed settings are different
        if ((changes.getAcceptAllCertificates() != null) &&
                (currentSettings.isAcceptAllCertificates() ^ changes.getAcceptAllCertificates().booleanValue())) {
            try {
                KeyStoreUtil.setAcceptAllCertificates(coordConfigStoringHelper,
                        changes.getAcceptAllCertificates());
            } catch (Exception e) {
                throw SecurityException.fatals.failedToSetTruststoreSettings(e);
            }
            auditTruststore(OperationTypeEnum.UPDATE_TRUSTSTORE_SETTINGS, changes);
        } else {
            throw APIException.badRequests
                    .mustHaveAtLeastOneChange(TrustedCertificateChanges.class.toString());
        }
    }

    /**
     * Get StorageOSUser from the security context
     * 
     * @return
     */
    private StorageOSUser getUserFromContext() {
        if (!hasValidUserInContext()) {
            throw APIException.forbidden.invalidSecurityContext();
        }
        return (StorageOSUser) sc.getUserPrincipal();
    }

    /**
     * Determine if the security context has a valid StorageOSUser object
     * 
     * @return true if the StorageOSUser is present
     */
    private boolean hasValidUserInContext() {
        if ((sc != null) && (sc.getUserPrincipal() instanceof StorageOSUser)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Record audit log for Truststore service
     * 
     * @param auditType
     *            Type of AuditLog
     * @param descparams
     *            Description paramters
     */
    private void auditTruststore(OperationTypeEnum auditType, Object... descparams) {
        URI username = URI.create(getUserFromContext().getName());
        auditMgr.recordAuditLog(null, username, EVENT_SERVICE_TYPE, auditType,
                System.currentTimeMillis(), AuditLogManager.AUDITLOG_SUCCESS, null,
                descparams);
    }

    /**
     * Record audit log for Truststore service
     * 
     * @param auditType
     *            Type of AuditLog
     * @param descparams
     *            Description paramters
     */
    private void auditTruststorePatialSuccess(OperationTypeEnum auditType,
            Object... descparams) {
        URI username = URI.create(getUserFromContext().getName());
        auditMgr.recordAuditLog(null, username, EVENT_SERVICE_TYPE, auditType,
                System.currentTimeMillis(), "PARTIAL_SUCCESS", null, descparams);
    }

    private KeyStore getKeyStore() {
        if (viprKeyStore == null) {
            try {
                viprKeyStore = KeyStoreUtil.getViPRKeystore(coordinator);
            } catch (Exception e) {
                log.error("Failed to load the VIPR keystore", e);
                throw new IllegalStateException(e);
            }
        }
        return viprKeyStore;
    }
}

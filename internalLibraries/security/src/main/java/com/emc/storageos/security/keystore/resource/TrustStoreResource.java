/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation All Rights Reserved
 * 
 * This software contains the intellectual property of EMC Corporation or is licensed to
 * EMC Corporation from third parties. Use of this software and the intellectual property
 * contained therein is expressly limited to the terms and conditions of the License
 * Agreement under which it is provided by or on behalf of EMC.
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

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, block_proxies = true)
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

    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, block_proxies = true)
    public TrustedCertificates addOrRemoveTrustedCertificate(
            TrustedCertificateChanges changes) {
        if (!coordinator.isClusterUpgradable()) {
            throw SecurityException.retryables.updatingKeystoreWhileClusterIsUnstable();
        }

        KeyStore keystore = getKeyStore();

        List<String> failedToParse = new ArrayList<String>();
        List<String> removeAliasDoesNotExist = new ArrayList<String>();
        List<String> added = new ArrayList<String>();
        List<String> removed = new ArrayList<String>();
        if (changes.getAdd() != null) {
            for (String certString : changes.getAdd()) {
                try {
                    Certificate cert =
                            KeyCertificatePairGenerator.getCertificateFromString(certString);
                    // if we were able to parse the cert, and there wasn't more that 1
                    // cert in this certificate entry
                    if (cert != null
                            && StringUtils.countMatches(certString,
                                    KeyCertificatePairGenerator.PEM_BEGIN_CERT) == 1) {
                        String alias = DigestUtils.sha512Hex(cert.getEncoded());
                        if (!keystore.containsAlias(alias)) {
                            X509Certificate x509cert = (X509Certificate) cert;
                            Date now = new Date();
                            if (x509cert.getNotAfter().before(now)) {
                                log.warn("The following certificate has expired, but will still be added to the truststore: "
                                        + x509cert.toString());
                            } else if (now.before(x509cert.getNotBefore())) {
                                log.warn("The following certificate is not yet valid, but will still be added to the truststore: "
                                        + x509cert.toString());
                            }
                            keystore.setCertificateEntry(alias, cert);
                            added.add(certString);
                        }
                    } else {
                        failedToParse.add(certString);
                    }
                } catch (KeyStoreException e) {
                    throw new IllegalStateException("keystore is not initialized", e);
                } catch (CertificateException e) {
                    log.debug(e.getMessage(), e);
                    failedToParse.add(certString);
                }
            }
        }
        if (changes.getRemove() != null) {
            for (String certString : changes.getRemove()) {
                Certificate cert;
                try {
                    cert =
                            KeyCertificatePairGenerator
                                    .getCertificateFromString(certString);
                    if (cert != null
                            && StringUtils.countMatches(certString,
                                    KeyCertificatePairGenerator.PEM_BEGIN_CERT) == 1) {
                        keystore.deleteEntry(DigestUtils.sha512Hex(cert.getEncoded()));
                        removed.add(certString);
                    } else {
                        failedToParse.add(certString);
                    }
                } catch (CertificateException e) {
                    log.warn("the following certificate could not be deleted: "
                            + certString, e);
                    failedToParse.add(certString);
                } catch (KeyStoreException e) {
                    log.warn("the following certificate could not be deleted: "
                            + certString, e);
                    removeAliasDoesNotExist.add(certString);
                }

            }
        }

        if (!CollectionUtils.isEmpty(added)
                && getTruststoreSettings().isAcceptAllCertificates()) {
            TruststoreSettingsChanges settingsChanges = new TruststoreSettingsChanges();
            settingsChanges.setAcceptAllCertificates(false);
            changeSettingInternal(settingsChanges);
        }

        recordTrustChangeIfAny(added, removed);

        // if something failed
        if (!CollectionUtils.isEmpty(failedToParse)
                || !CollectionUtils.isEmpty(removeAliasDoesNotExist)) {
            // if we had a partial success
            if (!CollectionUtils.isEmpty(added) || !CollectionUtils.isEmpty(removed)) {
                auditTruststorePatialSuccess(
                        OperationTypeEnum.UPDATE_TRUSTED_CERTIFICATES_PARTIAL, added, removed);
            }
            throw APIException.badRequests.truststoreUpdatePartialSuccess(failedToParse,
                    removeAliasDoesNotExist);
        } else if (!CollectionUtils.isEmpty(added) || !CollectionUtils.isEmpty(removed)) {
            // this is a complete success and some operations actually happened
            auditTruststore(OperationTypeEnum.UPDATE_TRUSTED_CERTIFICATES, changes);
        }
        return getTrustedCertificates();
    }

    /**
     * set a flag in zk if any change happened.
     * 
     * @param added
     * @param removed
     */
    private void recordTrustChangeIfAny(List<String> added, List<String> removed) {

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

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, block_proxies = true)
    @Path("/settings")
    public TruststoreSettings getTruststoreSettings() {
        TruststoreSettings settings = new TruststoreSettings();
        settings.setAcceptAllCertificates(KeyStoreUtil.getAcceptAllCerts(coordConfigStoringHelper));
        return settings;
    }

    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, block_proxies = true)
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

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/* 
 Copyright (c) 2012 EMC Corporation
 All Rights Reserved

 This software contains the intellectual property of EMC Corporation
 or is licensed to EMC Corporation from third parties.  Use of this
 software and the intellectual property contained therein is expressly
 imited to the terms and conditions of the License Agreement under which
 it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vasa;

import java.io.File;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.apache.log4j.Logger;

import com.emc.storageos.vasa.util.FaultUtil;
import com.emc.storageos.vasa.util.SSLUtil;
import com.emc.storageos.vasa.util.SessionContext;
import com.emc.storageos.vasa.util.internal.SimpleTimeCounter;
import com.vmware.vim.vasa._1_0.InvalidArgument;
import com.vmware.vim.vasa._1_0.InvalidCertificate;
import com.vmware.vim.vasa._1_0.InvalidLogin;
import com.vmware.vim.vasa._1_0.InvalidSession;
import com.vmware.vim.vasa._1_0.StorageFault;
import com.vmware.vim.vasa._1_0.data.xsd.HostInitiatorInfo;
import com.vmware.vim.vasa._1_0.data.xsd.MessageCatalog;
import com.vmware.vim.vasa._1_0.data.xsd.MountInfo;
import com.vmware.vim.vasa._1_0.data.xsd.StorageCatalogEnum;
import com.vmware.vim.vasa._1_0.data.xsd.UsageContext;
import com.vmware.vim.vasa._1_0.data.xsd.VasaProviderInfo;
import com.vmware.vim.vasa._1_0.data.xsd.VendorModel;

public class ContextManagerImpl implements ContextManager {

    /** Class logger */
    private static Logger log = Logger.getLogger(ContextManagerImpl.class);

    /** Singleton instance */
    private static ContextManagerImpl _instance;

    private VasaProviderInfo _vpInfo;
    private SSLUtil _sslUtil;
    private SOSManager _sosManager;

    private final String CATALOG_CONTEXT_URI = "/storageos-vasasvc/catalog/"; // NOSONAR
                                                                              // ("Suppressing Sonar violation of Lazy initialization of static fields should be synchronized")

    public static synchronized ContextManagerImpl getInstance() {
        if (_instance == null) {
            _instance = new ContextManagerImpl();
        }
        return _instance;
    }

    /** Constructor */
    private ContextManagerImpl() {
    }

    @Override
    public void init(SSLUtil sslUtil) {
        this._sslUtil = sslUtil;
    }

    @Override
    public MessageCatalog[] queryCatalog() throws StorageFault, InvalidSession {

        final String methodName = "queryCatalog(): ";
        log.debug(methodName + "Entry");
        // verify valid SSL and VASA Sessions.
        List<MessageCatalog> mcList = new ArrayList<MessageCatalog>();

        String fs = System.getProperty("file.separator");

        final String productHome = System.getProperty("product.home");
        final String catalogDirPath = productHome + fs + "lib" + fs
                + "storageos-vasasvc" + fs + "catalog";

        // Determine base Catalog directory
        String catalogDirStr = "";
        String catalinaHome = System.getProperty("server.home");
        if (catalinaHome != null) {
            // Ex: O:\Program Files\Apache\Tomcat 6.0
            catalogDirStr = catalogDirPath;

        } else {
            try {
                // Get the base dir of the running code
                URL url = getClass().getProtectionDomain().getCodeSource()
                        .getLocation();
                File catalogDir = new File(url.getPath());
                catalogDirStr = catalogDir.getCanonicalPath();

                if (catalogDirStr.contains("%20")) {
                    catalogDirStr = catalogDirStr.replace("%20", " ");
                }

            } catch (Exception e) {
                log.error(methodName
                        + "Exception attempting to locate catalog files", e);
                throw FaultUtil.StorageFault("runtime", e);

            }

        }

        if (catalogDirStr != null && catalogDirStr.length() > 0) {
            File catalogDir = new File(catalogDirStr);
            mcList = compileCatalogInformation(catalogDir, mcList);
        }

        log.debug(methodName + "Exit returning message catalog list of size["
                + mcList.size() + "]");
        return (MessageCatalog[]) mcList.toArray(new MessageCatalog[0]);
    }

    private List<MessageCatalog> compileCatalogInformation(File catalog,
            List<MessageCatalog> mcList) throws StorageFault {

        final String methodName = "compileCatalogInformation(): ";

        log.trace(methodName + "Entry with input(s) catalog[" + catalog
                + "] mcList of size[" + mcList.size() + "]");

        if (catalog != null && catalog.exists()) {
            if (catalog.isDirectory()) {
                String[] children = catalog.list();
                for (int i = 0; i < children.length; i++) {
                    compileCatalogInformation(new File(catalog, children[i]),
                            mcList);
                }
            } else {
                try {
                    MessageCatalog mc = new MessageCatalog();
                    mc.setModuleName(Constants.VASA_BOURNE_PROVIDER_CATAGLOG_NAME);
                    mc.setCatalogVersion(Constants.VASA_BOURNE_PROVIDER_CATAGLOG_VERSION);

                    // Catalog Locale
                    String catalogLocale = catalog.toURI().toString();
                    catalogLocale = catalogLocale.substring(
                            catalogLocale.indexOf("catalog") + 8,
                            catalogLocale.lastIndexOf("/"));
                    mc.setLocale(catalogLocale);

                    // NOTE:: catalogName must match constants defined in
                    // StorageCatalogEnum
                    // only event and alarm catalogs are required.
                    if (catalog.getName().equalsIgnoreCase("event.vmsg")) {
                        mc.setCatalogName(StorageCatalogEnum.Event.getValue());
                    } else if (catalog.getName().equalsIgnoreCase("alarm.vmsg")) {
                        mc.setCatalogName(StorageCatalogEnum.Alarm.getValue());
                    } else if (catalog.getName().equalsIgnoreCase("fault.vmsg")) {
                        mc.setCatalogName(StorageCatalogEnum.Fault.getValue());
                    } else {
                        log.warn(methodName + "catalog: " + catalog.getName()
                                + " is not supported!");
                        log.trace(methodName
                                + "Exit returning message catalog list of size["
                                + mcList.size() + "]");
                        return mcList;
                    }

                    // Catalog URI
                    String catalogURI = CATALOG_CONTEXT_URI + catalogLocale
                            + "/" + catalog.getName();
                    mc.setCatalogUri(catalogURI);

                    // Catalog Last Modified
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(catalog.lastModified());
                    mc.setLastModified(calendar);
                    mcList.add(mc);
                } catch (Exception e) {
                    log.error(methodName
                            + "Exception attempting to locate catalog files", e);
                    throw FaultUtil.StorageFault("runtime", e);
                }
            }
        }
        log.trace(methodName + "Exit returning message catalog list of size["
                + mcList.size() + "]");
        return mcList;
    }

    /**
     * vasaService interface
     */
    @Override
    public VasaProviderInfo registerVASACertificate(String username,
            String password, String certificateStr) throws InvalidCertificate,
            InvalidLogin, InvalidSession, StorageFault {

        final String methodName = "registerVASACertificate() :";

        log.debug(methodName + "Entry with inputs username[" + username
                + "] password[" + (password != null ? "****" : null)
                + "] certificateStr["
                + (certificateStr != null ? "****" : null) + "]");

        try {

            _sosManager = new SOSManager();

            /*
             * Verify username/password before verifying certificate. This means
             * that if both username/password and certificate are invalid
             * InvalidLogin exception will be thrown.
             */

            _sosManager.verifyLoginCredentials(username, password);

            log.debug(methodName
                    + "Valid username and password. User credentials accepted.");

            String clientAddress = _sslUtil.checkHttpRequest(false, false);

            X509Certificate x509Cert = (X509Certificate) _sslUtil
                    .buildCertificate(certificateStr);
            x509Cert.checkValidity();

            if (!_sslUtil.certificateIsTrusted((Certificate) x509Cert)) {
                _sslUtil.addCertificateToTrustStore(clientAddress,
                        (Certificate) x509Cert);

                log.trace(methodName + "new certificate added as trusted");
                _sslUtil.refreshTrustStore();
                invalidateSession();
            } else {
                log.trace(methodName + "certificate was already trusted");
            }

            log.trace(methodName + "vpInfo: defaultNameSpace["
                    + _vpInfo.getDefaultNamespace() + "] name["
                    + _vpInfo.getName() + "] sessionId["
                    + _vpInfo.getSessionId() + " vasaApiVersion["
                    + _vpInfo.getVasaApiVersion() + "] vasaProviderVersion["
                    + _vpInfo.getVasaProviderVersion() + "]");
            log.debug(methodName + "Exit returning vpInfo");

            return _vpInfo;
        } catch (InvalidSession is) {
            // thrown by sslUtil.checkHttpRequest()
            log.error(methodName + "Session is invalid", is);
            throw is;
        } catch (InvalidCertificate ic) {
            // thrown by sslUtil.buildCertificate()
            log.error(methodName + "Certificate is invalid", ic);
            throw ic;
        } catch (CertificateExpiredException e) {
            // thrown by x509Cert.checkValidity()
            log.error(methodName + "Certificate is expired", e);
            throw FaultUtil.InvalidCertificate(e);
        } catch (CertificateNotYetValidException e) {
            // thrown by x509Cert.checkValidity()
            log.error(methodName + "Certificate is not in validity period ", e);
            throw FaultUtil.InvalidCertificate(e);
        } catch (InvalidLogin il) {
            // thrown by verifyPassword();
            log.error(methodName + "Invalid login", il);
            throw il;
        } catch (Exception e) {
            log.error(methodName + "registration failed: ", e);
            throw FaultUtil.StorageFault(methodName + "registration failed: ",
                    e);
        }
    }

    /**
     * vasaService interface
     */
    @Override
    public VasaProviderInfo setContext(UsageContext uc) throws InvalidArgument,
            InvalidSession, StorageFault {

        final String methodName = "setContext(): ";

        log.debug(methodName + "Entry");

        if (log.isDebugEnabled()) {

            HostInitiatorInfo[] hostInitiators = uc.getHostInitiator();
            if (hostInitiators != null && hostInitiators.length > 0) {

                log.debug(methodName + "list of host initiators (HBA):");
                for (HostInitiatorInfo initiator : hostInitiators) {
                    log.debug(methodName + "id["
                            + initiator.getUniqueIdentifier() + "] iSCSIId["
                            + initiator.getIscsiIdentifier() + "] nodeWWN["
                            + initiator.getNodeWwn() + "] portWWN["
                            + initiator.getPortWwn() + "]");
                }
            }

            String[] iscsiIpAddresseses = uc.getIscsiInitiatorIpAddress();

            if (iscsiIpAddresseses != null) {
                log.debug(methodName + "list of iSCSI initiator IP addresses:");
                log.debug(methodName + Arrays.asList(iscsiIpAddresseses));
            }

            MountInfo[] mountPoints = uc.getMountPoint();
            if (mountPoints != null && mountPoints.length > 0) {
                log.debug(methodName + "List of file share mount paths:");
                for (MountInfo mountPoint : mountPoints) {
                    log.debug(methodName + "filePath["
                            + mountPoint.getFilePath() + "] serverName["
                            + mountPoint.getServerName() + "]");
                }
            }

            log.debug(methodName + "vSphere service instance Id["
                    + uc.getVcGuid() + "]");
        }
        String clientAddress = "unknown";
        try {
            // _sslUtil.checkForUniqueVASASessionId();
            SessionContext sc = null;
            clientAddress = _sslUtil.checkHttpRequest(true, false);

            // Tear down any existing session
            log.debug(methodName + "Tear down any existing session");
            invalidateSession();

            // Create new session.
            sc = SessionContext.createSession(uc, clientAddress);

            log.trace(methodName + "Created a new session: "
                    + sc.getSessionId());

            sc.setSosManager(_sosManager);

            log.trace(methodName + "SOS manager is set in session context: "
                    + sc.getSessionId());

            _vpInfo.setSessionId(sc.getSessionId());

            _sslUtil.setHttpResponse(sc);

            log.debug(methodName
                    + "Exit returning VasaProviderInfo with new session Id["
                    + _vpInfo.getSessionId() + "]");
            return _vpInfo;
        } catch (StorageFault sf) {
            log.error(methodName + " unknown exception occured", sf);
            throw sf;
        } catch (InvalidSession is) {
            log.error(methodName + "Invalid session exception", is);
            throw is;
        } catch (Exception e) {
            log.error("unknown exception occured", e);
            throw FaultUtil.StorageFault("runtime ", e);
        }
    }

    /**
     * remove the current Session context and VASA_SESSION_ID cookie
     */
    private void invalidateSession() throws InvalidSession {
        final String methodName = "invalidateSession(): ";

        log.trace(methodName + "Entry");
        try {
            String sessionId = _sslUtil.getCookie(SSLUtil.VASA_SESSIONID_STR);
            log.debug(methodName + "Got existing session ID from cookie["
                    + sessionId + "]");
            if (sessionId != null) {
                SessionContext.removeSession(sessionId);
            }
            log.trace(methodName + "Removed session ID [" + sessionId
                    + "] from session ID list");
            _sslUtil.setCookie(SSLUtil.VASA_SESSIONID_STR,
                    SessionContext.INVALID_SESSION_ID);
            log.trace(methodName + "Set value of current session ID as invalid");
            _vpInfo.setSessionId(SessionContext.INVALID_SESSION_ID);
        } catch (Exception e) {
            log.error(methodName + "Could not find session context", e);
            throw FaultUtil.InvalidSession("Could not find session context "
                    + e);
        }
        log.trace(methodName + "Exit");
    }

    /**
     * internal routine to perform Certificate unregister operation
     * 
     * @throws InvalidSession
     */
    private void unregisterCertificate(X509Certificate x509Cert)
            throws InvalidCertificate, StorageFault, InvalidSession {

        final String methodName = "unregisterCertificate(): ";

        log.trace(methodName + "Entry with input x509Cert["
                + (x509Cert != null ? "***" : null) + "]");
        try {

            if (_sslUtil.certificateIsTrusted(x509Cert)) {
                log.debug(methodName + "certificate removed from trusted");

                _sslUtil.removeCertificateFromTrustStore((Certificate) x509Cert);
                _sslUtil.refreshTrustStore();
                if (_sslUtil.certificateIsTrusted(x509Cert)) {
                    throw FaultUtil
                            .StorageFault("Certificate could not be removed from the trustStore.");
                }
                invalidateSession();
            } else {

                throw FaultUtil.InvalidCertificate("Certificate not registered.");

            }
        } catch (InvalidCertificate ic) {
            log.error(methodName + "Invalid certificate exception", ic);
            throw ic;
        } catch (InvalidSession is) {
            log.error(methodName + "Invalid session exception", is);
            throw is;
        } catch (Exception e) {
            log.error(methodName + "unknown exception occured", e);
            throw FaultUtil.StorageFault("runtime ", e);
        }
        log.trace(methodName + "Exit");
    }

    /**
     * vasaService interface
     */
    @Override
    public void unregisterVASACertificate(String existingCertificate)
            throws InvalidCertificate, InvalidSession, StorageFault {

        final String methodName = "unregisterVASACertificate(): ";

        log.debug(methodName + "Entry with input existingCertificate["
                + (existingCertificate != null ? "***" : null) + "]");

        try {

            /*
             * Need to have a valid SSL session, but VASA session not required
             */
            _sslUtil.checkHttpRequest(true, true);

            X509Certificate x509Cert = (X509Certificate) _sslUtil
                    .buildCertificate(existingCertificate);
            SimpleTimeCounter counter = new SimpleTimeCounter(
                    "unregisterVASACertificate");
            unregisterCertificate(x509Cert);
            counter.stop();

        } catch (InvalidSession is) {
            // thrown by unregisterCertificate()
            log.error(methodName + "invalid session", is);
            throw is;
        } catch (InvalidCertificate ic) {
            // thrown by sslUtil.buildCertificate()
            // thrown by unregisterCertificate()
            log.error(methodName + "invalid certificate", ic);
            throw ic;
        } catch (StorageFault sf) {
            log.error(methodName + "storage fault occured ", sf);
            throw sf;
        } catch (Exception e) {
            log.error(methodName + "unknown exception", e);
            throw FaultUtil.StorageFault("runtime ", e);
        }
        log.debug(methodName + "Exit");
    }

    /**
     * Craft the VASAProvider info
     */
    public VasaProviderInfo initializeVasaProviderInfo() {

        final String methodName = "initializeVasaProviderInfo(): ";

        log.debug(methodName + "Entry");

        VendorModel[] vms = new VendorModel[1];
        vms[0] = new VendorModel();
        vms[0].setVendorId(Constants.VASA_BOURNE_PROVIDER_VENDOR_NAME);
        vms[0].setModelId(Constants.VASA_BOURNE_PROVIDER_VENDOR_MODEL);

        _vpInfo = new VasaProviderInfo();

        _vpInfo.setVasaApiVersion(FaultUtil.getVasaApiVersion());
        log.debug(methodName + "vasa api version: "
                + _vpInfo.getVasaApiVersion());

        _vpInfo.setName(Constants.VASA_BOURNE_PROVIDER_NAME);
        log.debug(methodName + "vasa provider name: " + _vpInfo.getName());

        _vpInfo.setVasaProviderVersion(Constants.VASA_BOURNE_PROVIDER_VERSION);
        log.debug(methodName + "vasa provider version: "
                + _vpInfo.getVasaProviderVersion());

        _vpInfo.setDefaultSessionTimeoutInSeconds(SessionContext.DEFAULT_SESSION_TIMEOUT);
        log.debug(methodName + "default session time out (in seconds): "
                + _vpInfo.getDefaultSessionTimeoutInSeconds());

        String namespace = Constants.VASA_BOURNE_PROVIDER_NAMESPACE;
        _vpInfo.setDefaultNamespace(namespace);

        log.debug(methodName + "defaultNameSpace: "
                + _vpInfo.getDefaultNamespace());

        _vpInfo.setSupportedVendorModel(vms);

        log.debug(methodName + "Exit");
        return _vpInfo;
    }

    /**
     * called by vasaService APIs to verify the connection and get the
     * UsageContext.
     */
    public UsageContext getUsageContext() throws InvalidSession, StorageFault {

        final String methodName = "getUsageContext(): ";

        log.debug(methodName + "Entry");
        try {
            // verify valid SSL and VASA Sessions.

            String sessionId = _sslUtil.getCookie(SSLUtil.VASA_SESSIONID_STR);
            if (sessionId == null) {
                // this should "never happen" if checkHttpRequest does not
                // throw an exception
                throw FaultUtil.StorageFault("getUsageContext internal error.");
            }

            log.trace(methodName
                    + "Looking for sessin context by session id...");
            SessionContext sc = SessionContext
                    .lookupSessionContextBySessionId(sessionId);
            if (sc == null) {
                // this should "never happen" if checkHttpRequest does not
                // throw an exception
                throw FaultUtil.StorageFault("getUsageContext internal error.");
            }

            _sslUtil.setHttpResponse(sc);
            UsageContext uc = sc.getUsageContext();
            if (uc == null) {
                throw FaultUtil.StorageFault("UsageContext is not set");
            }
            // validateUsageContext(uc);
            return uc;
        } catch (StorageFault sf) {
            // thrown by this function
            log.error(methodName + "storage fault occured", sf);
            throw sf;
        } catch (InvalidSession is) {
            log.error(methodName + "invalid session", is);
            throw is;
        } catch (Exception e) {
            log.error(methodName + "unexpected error", e);
            throw FaultUtil.InvalidSession("runtime", e);
        }
    }

    public SOSManager getSOSManager() throws StorageFault, InvalidSession {

        final String methodName = "getSOSManager(): ";

        log.debug(methodName + "Entry");
        try {
            // verify valid SSL and VASA Sessions.

            String sessionId = _sslUtil.getCookie(SSLUtil.VASA_SESSIONID_STR);
            if (sessionId == null) {
                // this should "never happen" if checkHttpRequest does not
                // throw an exception
                throw FaultUtil.StorageFault(methodName + "internal error");
            }

            log.trace(methodName
                    + "Looking for session context by session id...");
            SessionContext sc = SessionContext
                    .lookupSessionContextBySessionId(sessionId);
            if (sc == null) {
                // this should "never happen" if checkHttpRequest does not
                // throw an exception
                throw FaultUtil.StorageFault("getUsageContext internal error.");
            }

            _sslUtil.setHttpResponse(sc);
            SOSManager sosManager = sc.getSosManager();
            if (sosManager == null) {
                throw FaultUtil.StorageFault("SOSManager is not set");
            }
            // validateUsageContext(uc);
            return sosManager;
        } catch (StorageFault sf) {
            // thrown by this function
            log.error(methodName + "storage fault occured", sf);
            throw sf;
        } catch (InvalidSession is) {
            log.error(methodName + "invalid session", is);
            throw is;
        } catch (Exception e) {
            log.error(methodName + "unexpected error", e);
            throw FaultUtil.InvalidSession("runtime", e);
        }

    }
}

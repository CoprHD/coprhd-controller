/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 **/
package com.emc.storageos.recoverpoint.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.cert.X509Certificate;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.net.www.protocol.http.AuthCacheImpl;
import sun.net.www.protocol.http.AuthCacheValue;

import com.emc.fapiclient.ws.FunctionalAPIImpl;
import com.emc.fapiclient.ws.FunctionalAPIImplService;

public class RecoverPointConnection {

    private static final String FAPI_URL = "http://impl.version4_1.fapi.emc.com/";
    private static final String FAPI_SERVICENAME = "FunctionalAPIImplService";

    private static final String SYSPROPS_HTTP_MAX_REDIRECTS = "http.maxRedirects";
    private static final String NEW_MAX_REDIRECTS = "2";

    private static Logger logger = LoggerFactory.getLogger(RecoverPointConnection.class);

    /**
     * Connect to RP and return a handle that can be used for FAPI calls
     *
     * @param endpoint - Address to connect to
     * @param username - Username for credentials
     * @param password - Password for credentials
     *
     * @return FunctionalAPIImpl - A handle for FAPI access
     *
     **/
    public FunctionalAPIImpl connect(URI endpoint, String username, String password) {

        try {
            ignoreCertifications();
            // interceptCertificates();
        } catch (Exception e) {
            // so what?
        }

        String destAddress = endpoint.toASCIIString();

        try {
            URL baseUrl = FunctionalAPIImplService.class.getResource(".");
            URL url = new URL(baseUrl, destAddress);

            final String finalUser = username;
            final String finalPassword = password;

            // Modify the System Property for Max Redirects to a smaller number so we don't hammer
            // the device over and over unnecessarily with bad credentials (if they're bad) as this
            // takes a long time. Default is 20 redirects.
            // However we will save the old redirect value and restore it after we're done.
            String oldMaxRedirectsValue = null;
            try {
                oldMaxRedirectsValue = System.getProperty(SYSPROPS_HTTP_MAX_REDIRECTS);
            } catch (NullPointerException npe) {
                logger.warn("The System property " + SYSPROPS_HTTP_MAX_REDIRECTS + " does not already exist for some reason.");
            }

            // Set the Property for http.maxRedirects to 2 to prevent unnecessary retries
            System.setProperty(SYSPROPS_HTTP_MAX_REDIRECTS, NEW_MAX_REDIRECTS);

            // If we're creating a new connection, we want to clear the cache as it may be holding
            // onto a previously valid connection.
            AuthCacheValue.setAuthCache(new AuthCacheImpl());
            Authenticator.setDefault(null);

            // Create a PasswordAuthentication so when the request is asked via HTTP for authentication,
            // the below will be automatically returned. This is set here, but invoked behind the scenes.
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(finalUser, finalPassword.toCharArray());
                }
            });

            logger.info("Attempting to connect to service " + FAPI_SERVICENAME + " at url " + FAPI_URL + " using auth credentials for: "
                    + finalUser);

            // Connect to the service
            FunctionalAPIImplService service = new FunctionalAPIImplService(url, new QName(FAPI_URL, FAPI_SERVICENAME));

            FunctionalAPIImpl impl = service.getFunctionalAPIImplPort();
            BindingProvider bp = (BindingProvider) impl;

            Map<String, Object> map = bp.getRequestContext();
            logger.info("RecoverPoint service: Dest: " + destAddress + ", user: " + username);
            map.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, destAddress);
            map.put(BindingProvider.USERNAME_PROPERTY, username);
            map.put(BindingProvider.PASSWORD_PROPERTY, password);

            // Reset the System Property for http.maxRedirects, but only if an existing value was present
            if (oldMaxRedirectsValue != null && !oldMaxRedirectsValue.isEmpty())
            {
                System.setProperty(SYSPROPS_HTTP_MAX_REDIRECTS, oldMaxRedirectsValue);
            }

            logger.info("Connected.");
            return impl;
        } catch (MalformedURLException e) {
            logger.error("Failed to create URL for the wsdl Location: " + destAddress);
            logger.error(e.getMessage());
            return null;
        }
    }

    // Generate a trust manager
    /**
     * @return
     */
    private X509TrustManager getTrustMangerForCertificates() {
        X509TrustManager tm = new X509TrustManager() {

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] arg0, String arg1)
                    throws java.security.cert.CertificateException {
                // TODO Auto-generated method stub

            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] ax509certificates, String arg1)
                    throws java.security.cert.CertificateException {
                // TODO Auto-generated method stub
                // for (X509Certificate certificate : ax509certificates) {
                // // Add to the certificate to keystore
                // addCertToKeyStore(certificate, alias);
                //
                //
                // }
                if (ax509certificates == null) {
                    logger.error("Null certificate received");
                    throw new java.security.cert.CertificateException();
                }
                for (java.security.cert.X509Certificate certificate : ax509certificates) {
                    // Don't display this, for security reasons. Uncomment if
                    // necessary.
                    // logger.info("Client certificate information:");
                    // logger.info("  Subject DN: " +
                    // certificate.getSubjectDN());
                    // logger.info("  Issuer DN: " + certificate.getIssuerDN());
                    // logger.info("  Serial number: " +
                    // certificate.getSerialNumber());
                    // logger.info("  Public Key: " +
                    // certificate.getPublicKey());
                    // logger.info("");
                    try {
                        addCertToKeyStore(certificate, certificate.getSerialNumber().toString());
                        break;
                    } catch (NoSuchAlgorithmException e) {
                        logger.error("NoSuchAlgorithmException Exception. Failed to add certificate to keystore");
                        throw new java.security.cert.CertificateException(); // NOSONAR
                    } catch (KeyStoreException e) {
                        logger.error("KeyStoreException Exception. Failed to add certificate to keystore");
                        throw new java.security.cert.CertificateException(); // NOSONAR
                    } catch (NoSuchProviderException e) {
                        logger.error("NoSuchProviderException Exception. Failed to add certificate to keystore");
                        throw new java.security.cert.CertificateException(); // NOSONAR
                    } catch (IOException e) {
                        logger.warn("IOException Exception. Failed to add certificate to keystore.  Assuming other thread did it.");
                    }
                }

            }
        };
        return tm;
    }

    // Add a certificate to the keystore
    /**
     * @param cert
     * @param alias
     * @throws NoSuchAlgorithmException
     * @throws java.security.cert.CertificateException
     * @throws KeyStoreException
     * @throws NoSuchProviderException
     * @throws IOException
     */
    private void addCertToKeyStore(java.security.cert.Certificate cert, String alias) throws NoSuchAlgorithmException,
            java.security.cert.CertificateException, KeyStoreException, NoSuchProviderException, IOException {
        // CertificateFactory cf = CertificateFactory.getInstance("X.509");

        // change this if you want another keystorefile by default
        String keystoreName = System.getProperty("keystore");

        // logger.info("addCertToKeyStore called for alias: " + alias);
        // logger.info("Cert: " + cert.toString());
        if (keystoreName == null) {
            // especially this ;-)
            keystoreName = System.getProperty("user.home") + System.getProperty("file.separator") + "keystore.ImportKey";
        }
        String keystorePassword = "changeit";
        // initializing keystore
        KeyStore ks = createAndLoadKeyStore(keystoreName, keystorePassword);
        java.security.cert.Certificate ksCrt = ks.getCertificate(alias);
        if (ksCrt != null) {
            ks.deleteEntry(alias);
        }

        // Add the certificate
        ks.setCertificateEntry(alias, cert);

        // Save the new keystore contents
        FileOutputStream out = new FileOutputStream(keystoreName);
        try {
            ks.store(out, keystorePassword.toCharArray());
        } finally {
            out.close();
        }
    }

    /**
     * @param keyStorePath
     * @param storePass
     * @return
     * @throws NoSuchAlgorithmException
     * @throws java.security.cert.CertificateException
     * @throws IOException
     * @throws KeyStoreException
     * @throws NoSuchProviderException
     */
    private KeyStore createAndLoadKeyStore(String keyStorePath, String storePass) throws NoSuchAlgorithmException,
            java.security.cert.CertificateException, IOException, KeyStoreException, NoSuchProviderException {
        // TODO Auto-generated method stub
        // initializing keystore
        KeyStore ks = KeyStore.getInstance("JKS", "SUN");
        ks.load(null, storePass.toCharArray());
        File storeFile = new File(keyStorePath);
        // logger.info("Create and load key store: " + keyStorePath +
        // " using password: " + storePass);
        if (!storeFile.exists()) {
            FileOutputStream osStorePath = new FileOutputStream(keyStorePath);
            try {
                ks.store(osStorePath, storePass.toCharArray());
            } finally {
                osStorePath.close();
            }
        }
        FileInputStream isStorePath = new FileInputStream(keyStorePath);
        try {
            ks.load(isStorePath, storePass.toCharArray());
        } finally {
            isStorePath.close();
        }
        return ks;
    }

    /**
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    @SuppressWarnings("unused")
    private void interceptCertificates() throws NoSuchAlgorithmException, KeyManagementException {
        // logger.info("Intercept certificates");
        TrustManager[] trustAllCerts = new TrustManager[1];
        trustAllCerts[0] = getTrustMangerForCertificates();

        // SSLContext sc = SSLContext.getInstance("SSL");
        SSLContext sc = SSLContext.getInstance("SSL");

        sc.init(null, trustAllCerts, null);

        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        HttpsURLConnection.setDefaultHostnameVerifier(new NullHostnameVerifier());
        // HttpsURLConnection.setDefaultHostnameVerifier(DO_NOT_VERIFY);
        System.setProperty("org.jboss.security.ignoreHttpsHost", "true");

    }

    private static class NullHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

    /**
     * Sets the https connection to trust all certifications.
     *
     * @throws KeyManagementException
     * @throws NoSuchAlgorithmException
     */
    // TODO - I don't think we want to ignore certificates in the final product.
    // May need to use 'keytool' to import certificates
    private void ignoreCertifications() throws KeyManagementException, NoSuchAlgorithmException {
        System.setProperty("javax.net.debug", "true");
        System.setProperty("org.jboss.security.ignoreHttpsHost", "true");
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String urlHostName, SSLSession session) {
                return true;
            }
        });

        trustAllHttpsCertificates();
    }

    /**
     * @throws KeyManagementException
     * @throws NoSuchAlgorithmException
     */
    private void trustAllHttpsCertificates() throws KeyManagementException, NoSuchAlgorithmException {
        // Create a trust manager that does not validate certificate chains:
        TrustManager[] trustAllCerts = new TrustManager[1];

        TrustManager tm = new AllTM();
        trustAllCerts[0] = tm;

        SSLContext sc = SSLContext.getInstance("SSL");

        sc.init(null, trustAllCerts, null);
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    }

    /**
     * This is a trust manager that trusts all certificates
     */
    private static class AllTM implements TrustManager, X509TrustManager {

        @SuppressWarnings("unused")
        public boolean isServerTrusted(X509Certificate[] certs) {
            return true;
        }

        @SuppressWarnings("unused")
        public boolean isClientTrusted(X509Certificate[] certs) {
            return true;
        }

        @SuppressWarnings("unused")
        public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
            return;
        }

        @SuppressWarnings("unused")
        public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
            return;
        }

        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] arg0, String arg1)
                throws java.security.cert.CertificateException {
            // TODO Auto-generated method stub

        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
            // TODO Auto-generated method stub

        }
    }

}

/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.net.ssl.*;
import java.security.SecureRandom;

/**
 * SSL Utilities such as trusting all SSL Certificates.
 */
public class SSLUtil {
    private static Logger log = LoggerFactory.getLogger(SSLUtil.class);

    private static boolean trustAllEnabled = false;
    private static SSLContext trustAllContext;
    private static SSLSocketFactory trustAllSslSocketFactory;
    private static NullHostNameVerifier nullHostnameVerifier;

    public static void setSSLSocketFactory(SSLSocketFactory factory) {
        HttpsURLConnection.setDefaultSSLSocketFactory(factory);
    }

    public static void trustAllSSLCertificates() {
        if (trustAllEnabled) {
            return;
        }

        setSSLSocketFactory(getTrustAllSslSocketFactory());
        trustAllEnabled = true;
    }

    public static void trustAllHostnames() {
        if (nullHostnameVerifier == null) {
            synchronized (SSLUtil.class) {
                if (nullHostnameVerifier == null) {
                    nullHostnameVerifier = getNullHostnameVerifier();
                    HttpsURLConnection.setDefaultHostnameVerifier(nullHostnameVerifier);
                }
            }
        }
    }

    public static NullHostNameVerifier getNullHostnameVerifier() {
        if (nullHostnameVerifier == null) {
            synchronized (SSLUtil.class) {
                if (nullHostnameVerifier == null) {
                    nullHostnameVerifier = new NullHostNameVerifier();
                }
            }
        }
        return nullHostnameVerifier;
    }

    public static SSLSocketFactory getTrustAllSslSocketFactory() {
        if (trustAllSslSocketFactory == null) {
            synchronized (SSLUtil.class) {
                if (trustAllSslSocketFactory == null) {
                    SSLContext sc = getTrustAllContext();
                    trustAllSslSocketFactory = sc.getSocketFactory();
                }
            }
        }
        return trustAllSslSocketFactory;
    }

    public static SSLContext getTrustAllContext() {
        if (trustAllContext == null) {
            synchronized (SSLUtil.class) {
                if (trustAllContext == null) {
                    try {
                        SSLContext sc = SSLContext.getInstance("SSL");
                        sc.init(null, newTrustManagers(), new SecureRandom());
                        trustAllContext = sc;
                    } catch (Exception e) {
                        log.error("Unable to register SSL TrustManager to trust all SSL Certificates", e);
                    }
                }
            }
        }
        return trustAllContext;
    }

    private static TrustManager[] newTrustManagers() {
        return new TrustManager[] { new AllTrustManager() };
    }

    private static class AllTrustManager implements X509TrustManager {

        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
        }

        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
        }
    }

    private static class NullHostNameVerifier implements HostnameVerifier {
        public boolean verify(String arg0, SSLSession arg1) {
            return true;
        }
    }
}

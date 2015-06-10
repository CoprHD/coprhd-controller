/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.util;

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
    private static NullHostNameVerifier hostnameVerifier;

    public static void setSSLSocketFactory(SSLSocketFactory factory) {
        HttpsURLConnection.setDefaultSSLSocketFactory(factory);
    }

    public static void trustAllSSLCertificates() {
        if (trustAllEnabled) {
            return;
        }

        SSLContext sc = getTrustAllContext();
        setSSLSocketFactory(sc.getSocketFactory());
        trustAllEnabled = true;
    }

    public static void trustAllHostnames() {
        if (hostnameVerifier == null) {
            hostnameVerifier = new NullHostNameVerifier();
            HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
        }
    }
    
    public static SSLContext getTrustAllContext() {
        if (trustAllContext == null) {
            try {
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, newTrustManagers(), new SecureRandom());
                trustAllContext = sc;
            }
            catch (Exception e) {
                log.error("Unable to register SSL TrustManager to trust all SSL Certificates", e);
            }
        }
        return trustAllContext;
    }
    
    public static TrustManager[] newTrustManagers() {
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

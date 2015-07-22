/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.net.ssl.*;
import java.security.SecureRandom;

/**
 * SSL Utilities such as trusting all SSL Certificates.
 */
public class SSLUtil {
    private static Logger log = LoggerFactory.getLogger(SSLUtil.class);

    private static SSLContext trustAllContext;

    public static synchronized SSLContext getTrustAllContext() {
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
}

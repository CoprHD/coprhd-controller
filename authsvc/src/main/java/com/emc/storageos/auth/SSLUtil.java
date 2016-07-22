/*
 * Copyright (c) 2012-2016 iWave Software LLC
 * All Rights Reserved
 */

package com.emc.storageos.auth;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class SSLUtil {

    public static SSLConnectionSocketFactory getSocketFactory(boolean bDisableSSL) {
        SSLConnectionSocketFactory sslsf;
        if (bDisableSSL) {
            SSLContext ctx;
            try {
                X509TrustManager x509TrustManager = new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    }
                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    }
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                };
                ctx = SSLContext.getInstance("TLS");
                ctx.init(new KeyManager[0], new TrustManager[]{x509TrustManager}, new SecureRandom());
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                System.out.println(e.getMessage());
                return null;
            }
            sslsf = new SSLConnectionSocketFactory(
                    ctx,
                    SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
            );
        } else {
            sslsf = SSLConnectionSocketFactory.getSocketFactory();
        }

        return sslsf;
    }

}

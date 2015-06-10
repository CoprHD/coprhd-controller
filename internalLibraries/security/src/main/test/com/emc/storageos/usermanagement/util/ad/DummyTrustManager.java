/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.usermanagement.util.ad;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class DummyTrustManager implements X509TrustManager {

    public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException
    {
        // do nothing
    }
    public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException
    {
        // do nothing
    }

    public X509Certificate[] getAcceptedIssuers()
    {
        return new java.security.cert.X509Certificate[0];
    }
}

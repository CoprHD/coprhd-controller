/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.connection;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;


class NonValidatingSocketFactory implements ProtocolSocketFactory {
    private SSLContext _sslContext;

    /**
     * Non validating trust manager
     */
    private static class NonValidatingTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            // no check
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            // no check
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    public NonValidatingSocketFactory() {
        try {
            _sslContext = SSLContext.getInstance("SSL");
            _sslContext.init(null, new TrustManager[] { new NonValidatingTrustManager() }, null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) throws IOException, UnknownHostException {
        return _sslContext.getSocketFactory().createSocket(s, i, inetAddress, i1);
    }

    @Override
    public Socket createSocket(String s, int i, InetAddress inetAddress, int i1, HttpConnectionParams httpConnectionParams)
            throws IOException, UnknownHostException, ConnectTimeoutException {
        int timeout = httpConnectionParams.getConnectionTimeout();
        Socket socket = _sslContext.getSocketFactory().createSocket();
        SocketAddress localaddr = new InetSocketAddress(inetAddress, i1);
        SocketAddress remoteaddr = new InetSocketAddress(s, i);
        socket.bind(localaddr);
        socket.connect(remoteaddr, timeout);
        return socket;
    }

    @Override
    public Socket createSocket(String s, int i) throws IOException, UnknownHostException {
        return _sslContext.getSocketFactory().createSocket(s, i);
    }
}


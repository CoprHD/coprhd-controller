/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.plugins.metering.vnxfile;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.net.SocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MySSLProtoSocketFactory to create socket to communicate over ssl using the
 * MyX509TrustManager to accept certificates.
 * 
 */

/*
 * Suppressing these warnings as fix will be made in future release.
 */
@SuppressWarnings({ "findbugs:EQ_GETCLASS_AND_CLASS_CONSTANT", "findbugs:MS_EXPOSE_REP", "pmd:MethodReturnsInternalArray" })
public class VNXFileProtocolSocketFactory implements ProtocolSocketFactory {

    /**
     * Logger instance to log messages.
     */
    public static Logger _logger = LoggerFactory
            .getLogger(VNXFileProtocolSocketFactory.class);

    private SSLContext _sslcontext = null;

    /**
     * Trust managers.
     */
    private static volatile TrustManager[] _trustManagers;

    /**
     * Constructor for EasySSLProtocolSocketFactory.
     */
    public VNXFileProtocolSocketFactory() {
        super();
    }

    /**
     * Create SSLContext using the TrustManager.
     * 
     * @return
     */
    private static SSLContext createEasySSLContext() {
        SSLContext context;
        try {
            context = SSLContext.getInstance("SSL");
            context.init(null, _trustManagers, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(context
                    .getSocketFactory());
        } catch (final GeneralSecurityException gse) {
            throw new IllegalStateException(gse.getMessage());
        }
        return context;
    }

    /**
     * creates SSLContext.
     * 
     * @return
     */
    private SSLContext getSSLContext() {
        if (_sslcontext == null) {
            _sslcontext = createEasySSLContext();
        }
        return _sslcontext;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress clientHost,
            int clientPort) throws IOException, UnknownHostException {

        return getSSLContext().getSocketFactory().createSocket(host, port,
                clientHost, clientPort);
    }

    @Override
    public Socket createSocket(final String host, final int port,
            final InetAddress localAddress, final int localPort,
            final HttpConnectionParams params) throws IOException,
            UnknownHostException, ConnectTimeoutException {
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        int timeout = params.getConnectionTimeout();
        SocketFactory socketfactory = getSSLContext().getSocketFactory();
        if (timeout == 0) {
            return socketfactory.createSocket(host, port, localAddress,
                    localPort);
        } else {
            Socket socket = socketfactory.createSocket();
            SocketAddress localaddr = new InetSocketAddress(localAddress,
                    localPort);
            SocketAddress remoteaddr = new InetSocketAddress(host, port);
            socket.bind(localaddr);
            socket.connect(remoteaddr, timeout);
            return socket;
        }
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException,
            UnknownHostException {
        return getSSLContext().getSocketFactory().createSocket(host, port);
    }

    /**
     * 
     */
    public boolean equals(Object obj) {
        return ((obj != null) && obj.getClass().equals(
                VNXFileProtocolSocketFactory.class));
    }

    /**
     * 
     */
    public int hashCode() {
        return VNXFileProtocolSocketFactory.class.hashCode();
    }

    /**
     * @return the _trustManager
     */
    public static TrustManager[] getTrustManagers() {
        return _trustManagers;
    }

    /**
     * @param _trustManager the _trustManager to set
     */
    public static void setTrustManagers(TrustManager trustManager) {
        if (null == _trustManagers) {
            _trustManagers = new TrustManager[] { trustManager };
        }
    }
}

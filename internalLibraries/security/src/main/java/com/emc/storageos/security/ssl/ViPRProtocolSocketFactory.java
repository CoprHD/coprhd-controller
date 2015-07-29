/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.ssl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;

/**
 * a protocol socket factory implementation that uses the ViPR keystore for accepting
 * certificates
 * NOTE: this object cannot be injected by spring, since it has a dependency on the keystore
 */
public class ViPRProtocolSocketFactory implements ProtocolSocketFactory {

    private final SSLSocketFactory sslSocketFactory;

    public ViPRProtocolSocketFactory(CoordinatorClient coordinator) {
        sslSocketFactory = new ViPRSSLSocketFactory(coordinator);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.commons.httpclient.protocol.ProtocolSocketFactory#createSocket(java.lang.String, int, java.net.InetAddress, int)
     */
    @Override
    public Socket createSocket(String host, int port, InetAddress localAddress,
            int localPort) throws IOException, UnknownHostException {
        return sslSocketFactory.createSocket(host, port, localAddress, localPort);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.commons.httpclient.protocol.ProtocolSocketFactory#createSocket(java.lang.String, int, java.net.InetAddress, int,
     * org.apache.commons.httpclient.params.HttpConnectionParams)
     */
    @Override
    public Socket createSocket(String host, int port, InetAddress localAddress,
            int localPort, HttpConnectionParams params) throws IOException,
            UnknownHostException, ConnectTimeoutException {

        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        int timeout = params.getConnectionTimeout();
        if (timeout == 0) {
            return sslSocketFactory.createSocket(host, port, localAddress, localPort);
        } else {
            Socket socket = sslSocketFactory.createSocket();
            SocketAddress localaddr = new InetSocketAddress(localAddress, localPort);
            SocketAddress remoteaddr = new InetSocketAddress(host, port);
            socket.bind(localaddr);
            socket.connect(remoteaddr, timeout);
            return socket;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.commons.httpclient.protocol.ProtocolSocketFactory#createSocket(java.lang.String, int)
     */
    @Override
    public Socket createSocket(String host, int port) throws IOException,
            UnknownHostException {
        return sslSocketFactory.createSocket(host, port);
    }
}

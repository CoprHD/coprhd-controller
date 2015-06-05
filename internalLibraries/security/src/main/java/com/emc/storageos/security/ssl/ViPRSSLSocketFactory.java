/**
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
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.keystore.impl.KeyStoreUtil;

/**
 * an SSL socket factory implementation that uses the ViPR keystore for accepting
 * certificates
 * NOTE: this object cannot be injected by spring, since it has a dependency on the keystore
 */
public class ViPRSSLSocketFactory extends SSLSocketFactory {

    private static Logger log = LoggerFactory.getLogger(ViPRSSLSocketFactory.class);

    private static CoordinatorClient coordinatorClient;
    private static ViPRSSLSocketFactory defaultInstance;

    private SSLSocketFactory socketFactory;

    /**
     * Sets the static coordinator client to be used when
     * 
     * @param coordinatorClient
     *            the coordinatorClient to set
     */
    public static void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        ViPRSSLSocketFactory.coordinatorClient = coordinatorClient;
    }

    public ViPRSSLSocketFactory(CoordinatorClient coordinator) {
        coordinatorClient = coordinator;
        init();
    }

    private void init() {

        // Use the ViPR trust manager
        X509TrustManager[] trustManagers = { new ViPRX509TrustManager(coordinatorClient) };
        try {
            KeyManagerFactory kmf =
                    KeyManagerFactory
                            .getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(KeyStoreUtil.getViPRKeystore(coordinatorClient), "".toCharArray());
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(kmf.getKeyManagers(), trustManagers, new SecureRandom());
            socketFactory = sc.getSocketFactory();
            log.debug("renewed ssl socket factory.");
        } catch (Exception e) {
            log.error("Error creating ViPRSSLSocketFactory", e);
        }
    }

    /* (non-Javadoc)
     * @see javax.net.ssl.SSLSocketFactory#createSocket(java.net.Socket, java.lang.String, int, boolean)
     */
    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose)
            throws IOException {
        log.debug("createSocket(Socket socket: " + socket + ",String host:" + host
                + ", int port:" + port + ",  boolean autoClose:" + autoClose + ")");

        return socketFactory.createSocket(socket, host, port, autoClose);
    }

    /* (non-Javadoc)
     * @see javax.net.ssl.SSLSocketFactory#getDefaultCipherSuites()
     */
    @Override
    public String[] getDefaultCipherSuites() {
        String[] defaultCipherSuites = socketFactory.getDefaultCipherSuites();
        log.debug("defaultCipherSuites are "
                + Arrays.asList(defaultCipherSuites).toString());
        return defaultCipherSuites;
    }

    /* (non-Javadoc)
     * @see javax.net.ssl.SSLSocketFactory#getSupportedCipherSuites()
     */
    @Override
    public String[] getSupportedCipherSuites() {
        String[] supportedCipherSuites = socketFactory.getSupportedCipherSuites();
        log.debug("supportedCipherSuites are "
                + Arrays.asList(supportedCipherSuites).toString());
        return supportedCipherSuites;
    }

    /* (non-Javadoc)
     * @see javax.net.SocketFactory#createSocket(java.lang.String, int)
     */
    @Override
    public Socket createSocket(String host, int port) throws IOException,
    UnknownHostException {
        log.debug("createSocket(String host:" + host + ", int port:" + port + ")");
        return socketFactory.createSocket(host, port);
    }

    /* (non-Javadoc)
     * @see javax.net.SocketFactory#createSocket(java.net.InetAddress, int)
     */
    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        log.debug("createSocket(InetAddress host:" + host + ", int port:" + port + ")");
        return socketFactory.createSocket(host, port);
    }

    /* (non-Javadoc)
     * @see javax.net.SocketFactory#createSocket(java.lang.String, int, java.net.InetAddress, int)
     */
    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException, UnknownHostException {
        log.debug("createSocket(String host:" + host + ", int port:" + port
                + ", InetAddress localHost:" + localHost + ", int localPort:" + localPort
                + ")");
        return socketFactory.createSocket(host, port, localHost, localPort);
    }

    /* (non-Javadoc)
     * @see javax.net.SocketFactory#createSocket(java.net.InetAddress, int, java.net.InetAddress, int)
     */
    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
            int localPort) throws IOException {
        log.debug("createSocket(InetAddress address:" + address + ", int port:" + port
                + ", InetAddress localAddress:" + localAddress + ", int localPort:"
                + localPort + ")");
        return socketFactory.createSocket(address, port, localAddress, localPort);
    }

    /**
     * Return the default instance of the ViPRSSLSocketFactory.
     * Should only be used after the StaticCoordinator has been set.
     * 
     * @return the default socket factory instance
     */
    public synchronized static SocketFactory getDefault() {
        if (null == defaultInstance) {
            log.debug("Creating new default instance");
            defaultInstance = new ViPRSSLSocketFactory(coordinatorClient);
        }
        return defaultInstance;
    }

}

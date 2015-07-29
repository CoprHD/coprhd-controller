/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright 2009, EMC Corporation ("EMC").
 * All Rights Reserved
 *
 * UNPUBLISHED CONFIDENTIAL AND PROPRIETARY PROPERTY OF EMC. The copyright
 * notice above does not evidence any actual or intended publication of this
 * software. Disclosure and dissemination are pursuant to separate agreements.
 * Unauthorized use, distribution or dissemination are strictly prohibited.
 */
package com.emc.cloud.http.ssl;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.ssl.ViPRX509TrustManager;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.AbstractHttpClient;

//import java.util.Properties;

/**
 * Helper for configuring SSL connections
 * 
 * @author perkib
 */
public class SSLHelper {

    /**
     * static only, no instances!
     */
    private SSLHelper() {
    }

    /**
     * Set the necessary options on an HttpClient instance to enable permissive
     * SSL connections. This implies disabling of trust validation, hostname
     * verification, and expiration checks.
     * <p>
     * WARNING: This is very handy for development and testing, but is NOT recommended for production code.
     * <p>
     * 
     * @param httpClient
     *            An HttpClient instance to be configured
     * @throws IllegalArgumentException
     *             if the argument is null
     * @throws GeneralSecurityException
     *             if an error occurs in changing the SSL settings
     */
    public static void configurePermissiveSSL(AbstractHttpClient httpClient)
            throws GeneralSecurityException {

        if (httpClient == null) {
            throw new IllegalArgumentException(
                    "null httpClient argument is not allowed");
        }

        // make https validation extremely permissive/insecure by disabling
        // checks for trusted certificates & host name validation
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[] { new PermissiveX509TrustManager(null) }, null);
            SSLSocketFactory socketFactory = new SupportedSSLSocketFactory(sslContext);
            socketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            // replace the default https scheme with our own
            Scheme sch = new Scheme("https", socketFactory, 443);
            httpClient.getConnectionManager().getSchemeRegistry().register(sch);

        } catch (GeneralSecurityException ex) {
            throw new GeneralSecurityException(
                    "Error updating https scheme with permissive settings", ex);
        }
    }

    public static void configureSSLWithTrustManger(AbstractHttpClient httpClient, CoordinatorClient coordinatorClient)
            throws GeneralSecurityException {

        if (httpClient == null || coordinatorClient == null) {

            if (httpClient == null) {
                throw new IllegalArgumentException(
                        "null httpClient argument is not allowed");
            }

            throw new IllegalArgumentException(
                    "null coordinatorClient is not allowed");
        }

        SSLContext sslContext;

        try {
            sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[] { new ViPRX509TrustManager(coordinatorClient) }, null);
            SSLSocketFactory socketFactory = new SupportedSSLSocketFactory(sslContext);
            socketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            Scheme sch = new Scheme("https", socketFactory, 443);
            httpClient.getConnectionManager().getSchemeRegistry().register(sch);

        } catch (GeneralSecurityException ex) {
            throw new GeneralSecurityException(
                    "Error updating https scheme with trust manager", ex);
        }
    }

    public static class SupportedSSLSocketFactory extends SSLSocketFactory {

        public SupportedSSLSocketFactory(SSLContext sslContext) {
            super(sslContext);
        }

        @Override
        public Socket createSocket() throws IOException {
            Socket socket = super.createSocket();

            return enableProtocols(socket);
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
            Socket newSocket = super.createSocket(socket, host, port, autoClose);

            return enableProtocols(newSocket);
        }

        /**
         * If the socket is an SSLSocket, set the list of accepted protocols.
         * 
         * @param socket
         * @return
         */
        private Socket enableProtocols(Socket socket) {
            if (socket instanceof SSLSocket) {
                SSLSocket sslSocket = (SSLSocket) socket;
                ArrayList<String> protocols = new ArrayList<String>();

                for (String protocol : sslSocket.getEnabledProtocols()) {
                    if (!protocol.startsWith(SSLV2)) {
                        protocols.add(protocol);
                    }
                }

                sslSocket.setEnabledProtocols((String[]) protocols.toArray(new String[protocols.size()]));
            }

            return socket;
        }
    }

}

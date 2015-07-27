/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
// Copyright 2012 by EMC Corporation ("EMC").
//
// UNPUBLISHED  CONFIDENTIAL  AND  PROPRIETARY  PROPERTY OF EMC. The copyright
// notice above does not evidence any actual  or  intended publication of this
// software. Disclosure and dissemination are pursuant to separate agreements.
// Unauthorized use, distribution or dissemination are strictly prohibited.

package com.emc.storageos.cimadapter.connections.cim;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import javax.cim.CIMInstance;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.wbem.listener.IndicationListener;
import javax.wbem.listener.WBEMListener;
import javax.wbem.listener.WBEMListenerFactory;

import org.sblim.cimclient.WBEMConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.ConnectionManagerException;
import com.emc.storageos.cimadapter.consumers.CimIndicationConsumer;
import com.emc.storageos.cimadapter.consumers.CimIndicationConsumerList;
import com.emc.storageos.cimadapter.processors.CimIndicationProcessor;
import com.emc.storageos.cimadapter.processors.CimIndicationSet;
import com.emc.storageos.services.ServicesConstants;

/**
 * CIM indication listener that hands off processing of each received indication
 * to the {@link CimConnection} that subscribed to that indication. If a
 * matching connection cannot be found, the indication is discarded.
 */
public class CimListener implements IndicationListener {

    // Flag indicates if the listener is running.
    private boolean _isRunning = false;

    // Flag indicates if the listener is paused.
    private volatile boolean _isPaused = false;

    // The URL for the listener.
    private URL _url;

    // A reference to the contained WBEM listener.
    private WBEMListener _listener;

    // The CIM indication queue.
    private Queue<CimQueuedIndication> _queue;

    // A map of the connection associated with the listener key'd by name.
    private ConcurrentMap<String, CimConnection> _connections;

    // A reference to the list of event consumers to which published indications
    // are sent.
    private CimIndicationConsumerList _indicationConsumers = null;

    // A logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(CimListener.class);
    
    private int defaultSMISSSLPort;
    
    // keystore and trust store locations
    private static String _keystoreLocation = System.getProperty(ServicesConstants.KEYSTORE_BASE_PATH_VARIABLE) + 
            ServicesConstants.KEYSTORE_FILE_NAME;
    private static String _trustStoreLocation = System.getProperty(ServicesConstants.TRUSTSTORE_BASE_PATH_VARIABLE) + 
            ServicesConstants.TRUSTSTORE_FILE_NAME;
    
    /**
     * Indications's thread pool to handle indications from smis.
     * Executors.newFixedThreadPool will give unbound thread pool. 
     * So no need to bother about RejectedExecutionHandler here.
     */
    private ExecutorService executorService = Executors.newFixedThreadPool(50);

    /**
     * Constructs a listener given the passed configuration.
     * 
     * @param info The listener configuration extracted from the Spring
     *        configuration file.
     * @param indicationConsumers The list of consumers to receive published
     *        events.
     */
    public CimListener(CimListenerInfo info, CimIndicationConsumerList indicationConsumers) {
        String hostIP = info.getHostIP();
        if ((hostIP != null) && (hostIP.length() != 0)) {
            String protocol = info.getProtocol();
            int port = info.getPort();
            try {
                _url = new URL(protocol, hostIP, port, "");
            } catch (Exception e) {
                s_logger.error("Error forming listener URL. Indications will not be received.", e);
            }
        } else {
            s_logger.error("Could not determine listener host. Indications will not be received.");
        }
        defaultSMISSSLPort = info.getDefaultSMISSSLPort();
        int queueSize = info.getQueueSize();
        _queue = new LinkedBlockingQueue<CimQueuedIndication>(queueSize);
        _connections = new ConcurrentHashMap<String, CimConnection>();
        _indicationConsumers = indicationConsumers;
    }

    /**
     * Getter for the listener's URL.
     * 
     * @return The listener's URL.
     */
    public URL getURL() {
        return _url;
    }

    /**
     * Registers a CIM connection with the listener. This allows the listener to
     * match incoming indications from that connection's subscriptions. This
     * assumes that the subscriptions are set up to include the connection name
     * in the destination URL.
     * 
     * @param connection The CIM connection to be registered.
     */
    public synchronized void register(CimConnection connection) {
        // For some reason, SBLIM CIM client forces the path in
        // indication URLs to lower case. This "bug" has been
        // reported, but hasn't been fixed as of version 2.1.8.
        // To match indications with connections, names must be
        // normalized to lowercase.
        String key = connection.getConnectionName().toLowerCase();
        _connections.put(key, connection);
        s_logger.info("Registered {}", connection.getConnectionName());
    }

    /**
     * Unregisters the given CIM connection.
     * 
     * @param connection The CIM connection to be unregistered.
     */
    public synchronized void unregister(CimConnection connection) {
        // TBD Is this a bug? The name is not forced to lowercase as in the
        // register method.
        _connections.remove(connection.getConnectionName());
        s_logger.info("Unregistered {}", connection.getConnectionName());
    }

    /**
     * Starts listening.
     * 
     * @throws IOException
     */
    public synchronized void startup() throws IOException {
        // Only start the listener if the host URL has been set.
        if (_url != null) {
            while (_listener == null) {
                s_logger.info("Starting listener at {}", _url);
                _listener = WBEMListenerFactory.getListener(CimConstants.CIM_CLIENT_PROTOCOL);
    
                // It can take a few attempts to reacquire the TCP port immediately
                // after releasing it.
                try {
                    String ecomProtocol = _url.getProtocol();
                    s_logger.info("ecomProtocol: {}", ecomProtocol);
                    if ("https".equalsIgnoreCase(ecomProtocol)) {
                        s_logger.info("Setting up secure listener port");
                        _listener.setProperty(WBEMConfigurationProperties.KEYSTORE_PATH, _keystoreLocation);
                        s_logger.info("keystore location: {}", _keystoreLocation);
                        _listener.setProperty(WBEMConfigurationProperties.KEYSTORE_PASSWORD, "changeit");
                        // truststore
                        _listener.setProperty(WBEMConfigurationProperties.SSL_LISTENER_PEER_VERIFICATION, "require");
                        _listener.setProperty(WBEMConfigurationProperties.TRUSTSTORE_PATH, _trustStoreLocation);
                        _listener.setProperty(WBEMConfigurationProperties.TRUSTSTORE_PASSWORD, "changeit");
                        s_logger.info("Enabled secure listener port");
                    } else {
                        s_logger.info("Enabled non-secure listener port");
                    }
                    _listener.addListener(this, _url.getPort(), ecomProtocol);
                } catch (BindException e) {
                    s_logger.error("Failed binding CIM listener", e);
                    try {
                        Thread.sleep(CimConstants.LISTENER_RETRY_INTERVAL);
                    } catch (InterruptedException ie) {
                    	s_logger.error(ie.getMessage(),ie);
                    }
                }
            }
    
            s_logger.info("Listening at {}", _url);
            _isRunning = true;
            _isPaused = false;
        }
        else {
            s_logger.error("Can't start listener. The host URL is not set.");
        }
    }

    /**
     * Forwards CIM indications to a matching connection. This only works if the
     * connection put its connection name in its destination URL when it
     * subscribed. An indication that has no matching connection is discarded.
     * 
     * When the listener is paused, indications are queued.
     * 
     * @param url The destination URL.
     * @param indication The CIM indication.
     * @param wasQueued true if this indication is from the queue, false
     *        otherwise.
     */
    private void indicationOccured(String url, CIMInstance indication, boolean wasQueued) {
        if (wasQueued) {
            s_logger.debug("{} Dequeued: {}", new Object[] { url, indication.toString() });
        } else {
            s_logger.debug("{} Received: {}", new Object[] { url, indication.toString() });
        }
        Runnable indicationWorker = new IndicationWorkerThread(url, indication, wasQueued);
        executorService.execute(indicationWorker);
    }
    
    /**
     * Worker thread to spawn indications into the processors through thread pool.
     */
    public class IndicationWorkerThread implements Runnable{
        
        String url;
        CIMInstance indication;
        boolean wasQueued;
        
        public IndicationWorkerThread(String url, CIMInstance indication, boolean wasQueued) {
            this.url = url;
            this.indication = indication;
            this.wasQueued = wasQueued;
        }

        @Override
        public void run() {
         // Awful quick-fix to filter out a nuisance.
            s_logger.debug("Inside IndicationWorkerThread");
            try {
                CimIndicationSet data = new CimIndicationSet(indication);
                if ((data.isAlertIndication()) && (data.containsKey(CimConstants.PROBABLE_CAUSE_TAG_KEY))) {
                    String probableCause = data.get(CimConstants.PROBABLE_CAUSE_TAG_KEY);
                    if ((probableCause != null) && (probableCause.equals(CimConstants.STATISTICAL_DATA_UPDATE_SUCCESS))) {
                        s_logger.info("{} Discarded: Statistical Data Update.", url);
                        return;
                    }
                }
            } catch (Exception ex) {
                s_logger.error("Error discarding statiustical data update", ex);
                return;
            }

            // Queue the indication if listening is paused.
            if (_isPaused) {
                if (_queue.offer(new CimQueuedIndication(url, indication))) {
                    s_logger.debug("{} Queued: {}", new Object[] { url, indication.toString() });
                } else {
                    s_logger.debug("Queue is full! {} Discarded: {}", new Object[] { url, indication.toString() });
                }
                return;
            }

            // The path SHOULD be a connection name.
            //
            // SBLIM CIM client version 2.1.7 only puts the path component
            // in the URL. That "bug" is fixed in version 2.1.8. Check the
            // URL with an inexpensive test until VOPS upgrades to using
            // version 2.1.8.
            //
            // Does the URL appear to have a scheme?
            String connectionName = url;
            if (url.indexOf("://") != -1) {
                try {
                    connectionName = new URL(url).getPath();
                } catch (Exception e) {
                	s_logger.error(e.getMessage(),e);
                }
            }

            // Look for a matching, registered connection. Reject
            // the indication if there is no match.
            //
            // For some reason, SBLIM CIM client forces the path to
            // lowercase. This "bug" has been reported, but has not
            // been fixed as of version 2.1.8. To find a match, all
            // names must be normalized to lowercase.
            String key = connectionName.toLowerCase();
            if (key.startsWith("/")) {
                key = key.substring(1);
            }
            if (_connections.containsKey(key)) {
                CimConnection connection = _connections.get(key);
                publishIndication(indication, connection);
            } else {
                s_logger.debug("{} Rejected: {}", new Object[] { url, indication.toString() });
            }
        }
        
    }

    /**
     * Forwards received CIM indications to a matching connection.
     * 
     * @param url The destination URL.
     * @param indication The CIM indication.
     */
    public void indicationOccured(String url, CIMInstance indication) {
        s_logger.debug("Indication occurred for {}", url);

        indicationOccured(url, indication, false);
    }

    /**
     * Pauses processing. While paused, received indications are queued.
     */
    public synchronized void pause() {
        if (!_isRunning || _isPaused) {
            return;
        }

        s_logger.info("Pausing Listener.");
        _isPaused = true;
    }

    /**
     * Resumes processing. Queued indications are immediately processed.
     */
    public synchronized void resume() {
        if (!_isRunning || !_isPaused) {
            return;
        }

        s_logger.info("Resuming Listener.");

        _isPaused = false;
        while (!_queue.isEmpty()) {
            CimQueuedIndication element = _queue.remove();
            String url = element.getURL();
            CIMInstance indication = element.getIndication();
            indicationOccured(url, indication, true);
        }
    }

    /**
     * Stops listening and releases the TCP port.
     */
    public synchronized void stop() {
        if (_isRunning) {
            s_logger.info("Stopping listener at {}", _url);
            _listener.removeListener(_url.getPort());
            s_logger.info("Stopped listener at {}", _url);
            _isRunning = false;
            _listener = null;
        }
    }
    
    /**
     * close's exiting tcp secure port 7012 and re'opens new socket to indications from smi-s provider.
     * @throws IOException
     */
    public synchronized void restart() throws IOException{
        s_logger.info("listener restart initiated");
        stop();
        startup();
    }

    /**
     * Forwards the indication to the list of registered indication consumers.
     * Note that the indication is first processed as specified by the consumer
     * to transform the indication to the format expected by the consumer.
     */
    private void publishIndication(CIMInstance indication, CimConnection connection) {
        if (_indicationConsumers == null) {
            s_logger.error("Indication consumers list is null.");
            return;
        }

        // Loop over the consumers processing the indication as specified by
        // the consumer and then forwarding the processed indication to the
        // consumer.
        for (CimIndicationConsumer consumer : _indicationConsumers) {
            // Initialized the processed indication to the passed indication.
            // If no processing is specified by the consumer, the raw indication
            // is forwarded to the consumer.
            Object processedIndication = indication;

            // If the consumer specifies default processing should occur, this
            // is done first.
            CimIndicationProcessor processor = null;
            if (consumer.getUseDefaultProcessor()) {
                processor = connection.getDefaultIndicationProcessor();
                processedIndication = processor.process(indication);
            }

            // Now if a custom processor is specified, the custom processor is
            // called to do any further processing of the indication.
            processor = consumer.getIndicationProcessor();
            if (processor != null) {
                processedIndication = processor.process(processedIndication);
            }

            // Now forward the processed indication to the consumer.
            consumer.consumeIndication(processedIndication);
        }
    }
    
    /**
     * 
     * @param connectionInfo
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     * @throws IOException
     * @throws KeyManagementException
     * @throws ConnectionManagerException
     */
    public void getClientCertificate(CimConnectionInfo connectionInfo) 
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, 
            KeyManagementException, ConnectionManagerException {
        char[] passphrase;
        String passphraseStr = "changeit";
        passphrase = passphraseStr.toCharArray();
        KeyStore ks = getTrustStore(_trustStoreLocation, passphrase);
        SSLContext context = SSLContext.getInstance("TLS");
        TrustManagerFactory tmf = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        X509TrustManager defaultTrustManager = (X509TrustManager) tmf
                .getTrustManagers()[0];
        TrustedCertManager tm = new TrustedCertManager(defaultTrustManager);
        s_logger.debug("Created trust manager");
        context.init(null, new TrustManager[] { tm }, null);
        SSLSocketFactory factory = context.getSocketFactory();
        
        String smiHost = connectionInfo.getHost();
        int smiPort = defaultSMISSSLPort;
        if(connectionInfo.getUseSSL()){
            smiPort = connectionInfo.getPort();
        }

        s_logger.debug("Opening connection to {}:{}", smiHost, smiPort);
        SSLSocket socket = (SSLSocket) factory.createSocket(smiHost, smiPort);
        socket.setSoTimeout(10000);
        try {
            s_logger.debug("Starting SSL negotiation");
            socket.startHandshake();
            socket.close();
            socket = null;
        } catch (SSLException e) {
            // We ignore this exception. What we really need is the SSL
            // handshake results.
        } finally {
            if (socket != null) {
                socket.close();
            }
        }

        X509Certificate[] chain = tm.chain;
        if (chain == null) {
            s_logger.debug("Error getting client certificate chain");
            throw new ConnectionManagerException(
                    "Error getting client certificate chain");
        }
        X509Certificate cert0 = chain[0];
        String alias0 = smiHost + "-" + "1";
        ks.setCertificateEntry(alias0, cert0);
        s_logger.debug("Added a certificate to the truststore with alias: {}",
                alias0);
        File trustStoreOut = new File(_trustStoreLocation);
        if (trustStoreOut.exists()) {
            // Save the original truststore
            File trustStoreOutSaved = new File(_trustStoreLocation + "~");
            if (trustStoreOutSaved.exists()) {
                trustStoreOut.delete();
            }
            trustStoreOut.renameTo(trustStoreOutSaved);
        }
        OutputStream out2 = new FileOutputStream(_trustStoreLocation);
        ks.store(out2, passphrase);
        out2.close();
        s_logger.debug("Created/updated the trust store: {}",
                _trustStoreLocation);
        
        restart();
    }
    /**
     * Gives trustStore
     * @param trustStoreFileName
     * @param passphrase
     * @return
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     * @throws IOException
     */
    private static KeyStore getTrustStore(String trustStoreFileName, char[] passphrase) 
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        InputStream in = null;  // We will provide null when we need to create a new keystore
        File file = new File(trustStoreFileName);
        if (file.isFile() == true) {
            in = new FileInputStream(file);
        }
        s_logger.debug("Loading the truststore: {}", file);
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(in, passphrase);
        if (in != null) {
            in.close();
        }
        return ks;
    }

    private static class TrustedCertManager implements X509TrustManager {

        private final X509TrustManager tm;
        private X509Certificate[] chain;

        TrustedCertManager(X509TrustManager tm) {
            this.tm = tm;
        }

        public X509Certificate[] getAcceptedIssuers() {
            throw new UnsupportedOperationException();
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            throw new UnsupportedOperationException();
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        	if(chain != null){
        		this.chain = Arrays.copyOf(chain, chain.length);
        	}
            tm.checkServerTrusted(chain, authType);
        }
    }

}
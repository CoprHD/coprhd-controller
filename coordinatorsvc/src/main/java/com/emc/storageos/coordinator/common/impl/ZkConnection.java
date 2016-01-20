/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.common.impl;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.UnhandledErrorListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryUntilElapsed;

/**
 * Wraps CuratorFramework with spring friendly config setters and default
 * retry policy
 */
public class ZkConnection {
    private static final Logger _logger = LoggerFactory.getLogger(ZkConnection.class);

    // default sleep time is 5 seconds between connection attempts when we lose connection to cluster
    private static final int RETRY_INTERVAL_MS = 5 * 1000;
    // connection times out at 3 minutes
    private static final int DEFAULT_CONN_TIMEOUT = 180 * 1000;
    // session times out at 9 minutes, after Curator 2.3, the retry times will depends on
    // Session timeout value divide connection timeout value. Currently, set session timeout value to
    // 3 times of connection timeout value, which means will retry 3 times.
    private static final int DEFAULT_TIMEOUT_MS = 3 * DEFAULT_CONN_TIMEOUT;

    // zk cluster connection
    private CuratorFramework _zkConnection;

    private String _connectString;

    // zk timeout ms
    private int _timeoutMs = DEFAULT_TIMEOUT_MS;

    private String siteId;

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    /**
     * Set coordinator cluster node URI's and build a connector.
     * <p/>
     * Node URI should be specified as
     * <p/>
     * coordinator://<node ip>:<port>
     * 
     * @param server server URI list
     */
    public void setServer(List<URI> server) throws IOException {
        StringBuilder connectString = new StringBuilder();
        for (int i = 0; i < server.size(); i++) {
            URI uri = server.get(i);
            connectString.append(String.format("%1$s:%2$d,", uri.getHost(), uri.getPort()));
        }
        _connectString = connectString.substring(0, connectString.length() - 1);
    }

    /**
     * Set zk session timeout in ms
     * 
     * @param timeoutMs timeout in ms
     */
    public void setTimeoutMs(int timeoutMs) {
        _timeoutMs = timeoutMs;
    }

    /**
     * Builds zk connector. Note that this method does not initiate a connection. {@link ZkConnection#connect()} must be called to connect
     * to cluster.
     * <p/>
     * This separation is provided so that callbacks can be setup separately prior to connection to cluster.
     */
    public void build() {
        try {
            _zkConnection = CuratorFrameworkFactory.builder().connectString(_connectString)
                    .connectionTimeoutMs(DEFAULT_CONN_TIMEOUT)
                    .canBeReadOnly(true)
                    .sessionTimeoutMs(_timeoutMs).retryPolicy(
                            new RetryUntilElapsed(_timeoutMs, RETRY_INTERVAL_MS)).build();
            _zkConnection.getUnhandledErrorListenable().addListener(new UnhandledErrorListener() {
                @Override
                public void unhandledError(String message, Throwable e) {
                    _logger.warn("Unknown exception in curator stack", e);
                }
            });
            _zkConnection.getConnectionStateListenable().addListener(new ConnectionStateListener() {
                @Override
                public void stateChanged(CuratorFramework client, ConnectionState newState) {
                    _logger.info("Current connection state {}", newState);
                }
            });
        } catch (Exception e) {
            throw CoordinatorException.fatals.failedToBuildZKConnector(e);
        }
    }

    /**
     * Connect to ZK cluster. As long quorum of nodes are available,
     * client can talk to a cluster. If connection drop, this implementation will
     * continuously retry sleeping 5 seconds in between.
     */
    public synchronized void connect() {
        if (!_zkConnection.isStarted()) {
            _zkConnection.start();
        }
    }

    /**
     * Disconnect from ZK cluster
     */
    public synchronized void disconnect() {
        if (_zkConnection.isStarted()) {
            _zkConnection.close();
        }
    }

    /**
     * Get ZK connection
     * 
     * @return zk connection
     */
    public CuratorFramework curator() {
        return _zkConnection;
    }
}

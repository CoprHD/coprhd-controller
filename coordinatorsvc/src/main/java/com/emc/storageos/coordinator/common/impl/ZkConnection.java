/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.common.impl;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.services.util.FileUtils;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.UnhandledErrorListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryUntilElapsed;
import org.apache.curator.utils.EnsurePath;
import org.apache.zookeeper.data.Stat;

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

    // lock file name when updating site id file
    private static final String SITEID_LOCKFILE="site_id_lock";
    
    // zk cluster connection
    private CuratorFramework _zkConnection;

    private String _connectString;

    // zk timeout ms
    private int _timeoutMs = DEFAULT_TIMEOUT_MS;

    private String siteIdFile;
    private String siteId;

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }
    
    public void setSiteIdFile(String siteIdFile) {
        this.siteIdFile = siteIdFile;
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
            if (FileUtils.exists(siteIdFile)) {
                siteId = new String(FileUtils.readDataFromFile(siteIdFile));
                siteId = siteId.trim();
                _logger.info("Current site id is {}", siteId);
            }
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
        // check if site id exists
        if (StringUtils.isEmpty(siteId)) {
            generateSiteId();
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
    
    /**
     * Generate site unique id for current cluster. UUID is formed as 2 parts 
     *   - creation time of znode /sites
     *   - hashcode of list zk server IPs
     * The uuid is stored at a local file specified by siteIdFile. It was generated 
     * only once during first boot
     */
    private void generateSiteId() {
        try {
            // get creation time of znode /sites
            EnsurePath siteZkPath = new EnsurePath(ZkPath.SITES.toString());
            siteZkPath.ensure(curator().getZookeeperClient());
            Stat stat = curator().checkExists().forPath(ZkPath.SITES.toString());
            long ctime = stat.getCtime();
            
            // calculate hash code for node ip list
            int len = _connectString.length();
            int ipHashHigh = _connectString.substring(0, len / 2).hashCode();
            int ipHashLow = _connectString.substring(len / 2).hashCode();
            long ipHash = (((long)ipHashHigh) << 32) | (((long)ipHashLow) & 0x00000000FFFFFFFFL);
            
            siteId = createTimeUUID(ctime, ipHash);
            _logger.info("Site UUID is {}", siteId);
            
            if (!FileUtils.exists(siteIdFile)) {
                // grab a lock file before writing site id file
                String lockFile = FileUtils.generateTmpFileName(SITEID_LOCKFILE);
                if (!FileUtils.exists(lockFile)) {
                    FileUtils.writePlainFile(lockFile, "".getBytes());
                }
                Path path = Paths.get(lockFile);
                FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.WRITE);
                try (FileLock lock = fileChannel.lock()) {
                    FileUtils.writePlainFile(siteIdFile, siteId.getBytes());
                    _logger.info("Write site id {} to file", siteId);
                }
            }
        } catch (Exception ex) {
            _logger.error("Cannot generate site uuid", ex);
            throw CoordinatorException.fatals.failedToBuildZKConnector(ex);
        }
    }
    
    /**
     * Create is version 1 UUID(time based) 
     * 
     * @param timestamp timestamp in milliseconds
     * @param leastSigBits least significant bits for the uuid
     */
    private String createTimeUUID(long timestamp, long leastSigBits) {
        long mostSigBits;
        long timeToUse = (timestamp * 10000) + 0x01B21DD213814000L;
        // time low
        mostSigBits = timeToUse << 32;
        // time mid
        mostSigBits |= (timeToUse & 0xFFFF00000000L) >> 16;
        // time hi and version
        mostSigBits |= 0x1000 | ((timeToUse >> 48) & 0x0FFF); // version 1
        
        return new UUID(mostSigBits, leastSigBits).toString();
    }
}

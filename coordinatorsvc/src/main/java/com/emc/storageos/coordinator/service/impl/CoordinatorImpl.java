/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.emc.storageos.services.util.JmxServerWrapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.zookeeper.server.PurgeTxnLog;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServerMain;
import org.apache.zookeeper.server.admin.AdminServer;
import org.apache.zookeeper.server.quorum.QuorumPeerMain;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.ReaperLeaderSelectorListener;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.coordinator.service.Coordinator;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;

/**
 * Coordinator server implementation
 */
public class CoordinatorImpl implements Coordinator {
    private static final Log _log = LogFactory.getLog(CoordinatorImpl.class);

    private SpringQuorumPeerConfig _config;
    private CoordinatorClient _coordinatorClient;
    private JmxServerWrapper _jmxServer;
    private ZKMain server;

    // runs periodic snapshot cleanup
    private static final String PURGER_POOL = "SnapshotPurger";
    private ScheduledExecutorService _exe = new NamedScheduledThreadPoolExecutor(PURGER_POOL, 1);
    private static final String UNCOMMITTED_DATA_REVISION_FLAG = "/data/UNCOMMITTED_DATA_REVISION";
    /**
     * Set node / cluster config
     * 
     * @param config node / cluster config
     */
    // only called when Spring initialization, or in test case, safe to suppress.
    @SuppressWarnings("findbugs:IS2_INCONSISTENT_SYNC")
    public void setConfig(SpringQuorumPeerConfig config) {
        _config = config;
    }

    /**
     * Setter for the coordinator client reference.
     * 
     * @param coordinatorClient A reference to the coordinator client.
     */
    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        _coordinatorClient = coordinatorClient;
    }

    /**
     * JMX server wrapper
     */
    public void setJmxServerWrapper(JmxServerWrapper jmxServer) {
        _jmxServer = jmxServer;
    }

    @Override
    public synchronized void start() throws IOException {
        if (new File(UNCOMMITTED_DATA_REVISION_FLAG).exists()) {
            _log.error("Uncommitted data revision detected. Manual relink db/zk data directory");
            throw new RuntimeException("Uncommited data revision");
        }
        
        // Enable readonly mode if current node is reachable to others
        System.setProperty("readonlymode.enabled", String.valueOf(true));
        
        // snapshot clean up runs at regular interval and leaves desired snapshots
        // behind
        _exe.scheduleWithFixedDelay(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            PurgeTxnLog.purge(
                                    _config.getDataDir(),
                                    _config.getDataDir(), _config.getSnapRetainCount());
                        } catch (Exception e) {
                            _log.debug("Exception is throwed when purging snapshots and logs", e);
                        }
                    }
                },
                0, _config.getPurgeInterval(), TimeUnit.MINUTES);

        startMutexReaper();

        // Starts JMX server for analysis and data backup
        try {
            if (_jmxServer != null) {
                _jmxServer.start();
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        try {
            if (_config.getServers().size() == 0) {
                // standalone
                ServerConfig config = new ServerConfig();
                config.readFrom(_config);
                server = new ZKMain();
                server.runFromConfig(config);
            } else {
                // cluster
                QuorumPeerMain main = new QuorumPeerMain();
                main.runFromConfig(_config);
            }
            
            _log.info("coordinator service started");
        }catch(AdminServer.AdminServerException e) {
            _log.info("Fail to start ZK server e:", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Reaper mutex dirs generated from InterProcessMutex
     */
    private void startMutexReaper() {

        Thread childReaperThread = new Thread(new Runnable() {
            public void run() {
                try {
                    // Currently the coordinatorclient is connected
                    // from LoggingBean, needs to wait for CoordinatorSvc started.
                    while (!_coordinatorClient.isConnected()) {
                        _log.info("Waiting for connection to cluster ...");
                        Thread.sleep(3 * 1000);
                    }

                    _log.info("Connected to cluster");

                    /**
                     * Reaper empty dirs under /mutex in zookeeper
                     * It leverages curator Reaper and ChildReaper to remove empty sub dirs.
                     * It leverages LeaderSelector to assure only one reaper running at the same time.
                     * Note: Please use autoRequeue() to requeue for competing leader automatically
                     * while connection broken and reconnected. The requeue() has a bug in curator
                     * 1.3.4 and should not be used.
                     */
                    LeaderSelectorListener listener = new ReaperLeaderSelectorListener(ZkPath.MUTEX.toString());
                    String _leaderRelativePath = "mutexReaper";
                    LeaderSelector leaderSel = _coordinatorClient.getLeaderSelector(_leaderRelativePath, listener);
                    leaderSel.autoRequeue();
                    leaderSel.start();
                } catch (Exception e) {
                    _log.warn("reaper task threw", e);
                }
            }
        }, "reaper thread");

        childReaperThread.start();
    }

    @Override
    // This method is provided for shutdown hook thread only, safe to suppress
            @SuppressWarnings("findbugs:IS2_INCONSISTENT_SYNC")
            public
            void stop() {
        if (_log.isInfoEnabled()) {
            _log.info("Stopping coordinator service...");
        }

        _exe.shutdownNow();

        _coordinatorClient.stop();

        if (server != null) {
            server.stop();
        }

        if (_jmxServer != null) {
            _jmxServer.stop();
        }

        if (_log.isInfoEnabled()) {
            _log.info("Coordinator service stopped...");
        }
    }

    protected class ZKMain extends ZooKeeperServerMain {

        public void stop() {
            shutdown();
        }
    }
}

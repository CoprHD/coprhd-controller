/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.management.JMException;

import com.emc.storageos.services.util.JmxServerWrapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.zookeeper.jmx.ManagedUtil;
import org.apache.zookeeper.server.PurgeTxnLog;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZKDatabase;
import org.apache.zookeeper.server.ZooKeeperServerMain;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.quorum.QuorumPeer;
import org.apache.zookeeper.server.quorum.QuorumPeer.LearnerType;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
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
    // ZK client port if we use dual coordinator hack for 1+0. No one use this port at all
    private static final String DUAL_COORDINATOR_CLIENT_PORT="3181";  
    
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

        _log.info(String.format("%s: %s", SpringQuorumPeerConfig.READONLY_MODE_ENABLED,
                System.getProperty(SpringQuorumPeerConfig.READONLY_MODE_ENABLED)));
        
        // snapshot clean up runs at regular interval and leaves desired snapshots
        // behind
        _exe.scheduleWithFixedDelay(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            PurgeTxnLog.purge(
                                    new File(_config.getDataDir()),
                                    new File(_config.getDataDir()), _config.getSnapRetainCount());
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
        
        if (_config.getServers().size() == 0) {
            // standalone
            ServerConfig config = new ServerConfig();
            config.readFrom(_config);
            server = new ZKMain();
            server.runFromConfig(config);
        } else {
            // cluster 
            try {
                ManagedUtil.registerLog4jMBeans();
            } catch (JMException e) {
                _log.warn("Unable to register log4j JMX control", e);
            }
            try {
                runFromConfig(_config);
                
                // Dual coordinator - a hack for 1+0 (dev environment only) in DR. End customer uses 2+1 or 3+2 only and never goes into this case.
                //
                // We run 2 zookeeper instances for 1+0 to address a limitation in ZOOKEEPER-1692 in 3.4.6. ZK doesn't
                // allow observers for standalone zookeeper(single zk participants). So we run 2 zookeeper servers here to 
                // simulate 2 zk participants and bypass this limitation, then we are able to add zk observers for DR sites.
                // /etc/genconfig.d/coordinator generates 2 zk servers to coordinator-var.xml, and here we start
                // 
                // ZK 3.5 introduces new parameter standaloneEnabled to address this limitation. Need revisit this hack after upgraded to zk 3.5
                int serverCnt = _config.getNumberOfParitipants();
                if (serverCnt == 2 && _config.getPeerType().equals(LearnerType.PARTICIPANT)) {
                    _log.info("Starting the other peer to run zk in cluster mode. Aim to address a ZK 3.4.6 limitation(cannot add observers to standalone server)");
                    Properties prop = new Properties();
                    prop.setProperty("dataDir", _config.getDataDir() + "/peer2");
                    prop.setProperty("clientPort", DUAL_COORDINATOR_CLIENT_PORT); // a deferent port
                    SpringQuorumPeerConfig newConfig = _config.createNewConfig(prop, 2);
                    runFromConfig(newConfig);
                }
            } catch (Exception ex) {
                _log.error("Unexpected error when starting Zookeeper peer", ex);
                throw new IllegalStateException("Fail to start zookeeper", ex);
            }
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
                    DrUtil drUtil = new DrUtil(_coordinatorClient);
                    if (drUtil.isStandby()) {
                        _log.info("Skip mutex reapter on standby site");
                        return;
                    }

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
    
    // Start Zookeeper peer in cluster mode 
    private void runFromConfig(SpringQuorumPeerConfig config) throws Exception {
        _log.info(String.format("Starting quorum peer from config for %d", config.getServerId()));
        ServerCnxnFactory cnxnFactory = ServerCnxnFactory.createFactory();
        cnxnFactory.configure(config.getClientPortAddress(),
                              config.getMaxClientCnxns());
        
        QuorumPeer quorumPeer = new QuorumPeer();
        quorumPeer.setClientPortAddress(config.getClientPortAddress());
        quorumPeer.setTxnFactory(new FileTxnSnapLog(
                    new File(config.getDataLogDir()),
                    new File(config.getDataDir())));
        quorumPeer.setQuorumPeers(config.getServers());
        quorumPeer.setElectionType(config.getElectionAlg());
        quorumPeer.setMyid(config.getServerId());
        quorumPeer.setTickTime(config.getTickTime());
        quorumPeer.setMinSessionTimeout(config.getMinSessionTimeout());
        quorumPeer.setMaxSessionTimeout(config.getMaxSessionTimeout());
        quorumPeer.setInitLimit(config.getInitLimit());
        quorumPeer.setSyncLimit(config.getSyncLimit());
        quorumPeer.setQuorumVerifier(config.getQuorumVerifier());
        quorumPeer.setCnxnFactory(cnxnFactory);
        quorumPeer.setZKDatabase(new ZKDatabase(quorumPeer.getTxnFactory()));
        quorumPeer.setLearnerType(config.getPeerType());
        quorumPeer.setSyncEnabled(config.getSyncEnabled());
        quorumPeer.setQuorumListenOnAllIPs(config.getQuorumListenOnAllIPs());

        quorumPeer.start();
    }
}

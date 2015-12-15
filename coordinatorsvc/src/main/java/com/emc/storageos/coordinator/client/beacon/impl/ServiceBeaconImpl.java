/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.beacon.impl;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.beacon.ServiceBeacon;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.services.util.NamedThreadPoolExecutor;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.utils.EnsurePath;

/**
 * Default ServiceBeacon implementation
 */
public class ServiceBeaconImpl implements ServiceBeacon {
    private static final Logger _log = LoggerFactory.getLogger(ServiceBeaconImpl.class);

    // service path
    protected String _servicePath;

    // service parent path
    protected String _serviceParentPath;

    // service information
    protected ServiceImpl _service;

    // zk connection
    protected ZkConnection _zkConnection;

    private final ExecutorService _executor = new NamedThreadPoolExecutor(ServiceBeaconImpl.class.getSimpleName(), 1);

    // initialization flag
    private volatile boolean _bInitialized = false;

    private volatile boolean _bStarted = false;

    // Service beacon should be created to site specific area(/sites/<uuid>/service) since yoda. But in order to make sure 
    // rolling upgrade could work, we need temporarily create beacons at global area(/service). So we add this flag here
    // to indicate where the beacon should be created - site specific aread(default), or global area in zk
    private boolean siteSpecific = true;
    
    /**
     * Reacts to connect/reconnect events by registering if necessary
     */
    private final ConnectionStateListener _connectionListener = new ConnectionStateListener() {
        @Override
        public void stateChanged(CuratorFramework client, ConnectionState newState) {
            switch (newState) {
                case CONNECTED:
                case RECONNECTED: {
                    _executor.submit(new Callable<Object>() {
                        @Override
                        public Object call() throws Exception {
                            if (_bStarted) {
                                // We should not register beacon before start()
                                // zkConn is connected in init().
                                // The reconnection might happen before start() invoked,
                                // which means the service is not fully started yet.
                                register();
                            }
                            return null;
                        }
                    });
                    break;
                }
            }
        }
    };

    /**
     * Service setter
     * 
     * @param service service info
     */
    public void setService(ServiceImpl service) {
        _service = service;
    }

    public ServiceImpl getService() {
        return _service;
    }
    
    /**
     * Set ZK cluster connection. Connection must be built but not started when
     * this setter is called
     * 
     * @param zkConnection ZK cluster connection
     */
    public void setZkConnection(ZkConnection zkConnection) {
        _zkConnection = zkConnection;
    }

    public ZkConnection getZkConnection() {
        return _zkConnection;
    }
    
    public void setSiteSpecific(boolean siteSpecific) {
        this.siteSpecific = siteSpecific;
    }
    /**
     * Init method.
     * Add state change listener
     * Connect to zk cluster
     * Remove stale service registration from zk
     */
    public void init() {
        _bInitialized = true;

        _zkConnection.curator().getConnectionStateListenable().addListener(_connectionListener);
        _zkConnection.connect();

        if (siteSpecific) {
            _serviceParentPath = String.format("%1$s/%2$s%3$s/%4$s/%5$s",
                    ZkPath.SITES, _zkConnection.getSiteId(), ZkPath.SERVICE, _service.getName(), _service.getVersion());
        } else {
            _serviceParentPath = String.format("%1$s/%2$s/%3$s",
                    ZkPath.SERVICE, _service.getName(), _service.getVersion());
        }
        _servicePath = String.format("%1$s/%2$s", _serviceParentPath, _service.getId());

        try {
            checkStaleRegistration();
        } catch (Exception ex) {
            _log.warn("Unable to remove stale service registration", ex);
        }
    }

    /**
     * Check stale service registration that may exist in zk because of unclean shutdown.
     * Remove stale registration if there is. Return zk Stat object if the service has been
     * successfully registered before.
     * 
     * @return null if service registration does not exist, otherwise a stat instance
     *         to indicate zk path
     */
    private Stat checkStaleRegistration() throws Exception {
        Stat stat = _zkConnection.curator().checkExists().forPath(_servicePath);
        if (stat != null && stat.getEphemeralOwner() != _zkConnection.curator().
                getZookeeperClient().getZooKeeper().getSessionId()) {
            _zkConnection.curator().delete().forPath(_servicePath);
            _log.info("Deleted stale service registration from previous session");
            stat = null;
        }
        return stat;
    }

    protected boolean register() throws Exception {
        // whenever we get into connected state (implies we previously moved out of
        // connected state for some reason), we check service registration and
        // update if necessary
        try {
            _log.info("Registering Service in path: {}", this._servicePath);
            Stat stat = checkStaleRegistration();

            if (stat == null) {
                _zkConnection.curator().create().withMode(CreateMode.EPHEMERAL).
                        forPath(_servicePath, _service.serialize());
                _log.info("Service info registered");
                return true;
            }
        } catch (Exception e) {
            _log.error("register service error", e);
        }
        return false;
    }

    @Override
    public Service info() {
        return _service;
    }

    @Override
    public void start() {
        // make sure ServiceBeacon is initialized.
        if (!_bInitialized) {
            init();
        }

        try {
            EnsurePath path = new EnsurePath(_serviceParentPath);
            path.ensure(_zkConnection.curator().getZookeeperClient());
        } catch (Exception e) {
            throw CoordinatorException.fatals
                    .failedToConnectToServiceRegistrationEndpoint(e);
        }
        _executor.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                register();
                _bStarted = true;
                return null;
            }
        });
    }

    @Override
    public void stop() {
        try {
            _zkConnection.curator().delete().forPath(_servicePath);
        } catch (Exception e) {
            _log.warn("Unable to delete service registration", e);
        }
        _executor.shutdown();
        // shouldn't close connection since it is likely shared with some other service
    }
 
}

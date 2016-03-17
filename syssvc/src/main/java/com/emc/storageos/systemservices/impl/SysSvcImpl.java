/*
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl;

import org.apache.cassandra.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.beacon.ServiceBeacon;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.security.AbstractSecuredWebServer;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.systemservices.SysSvc;
import com.emc.storageos.systemservices.impl.audit.SystemAudit;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.ipreconfig.IpReconfigManager;
import com.emc.storageos.systemservices.impl.jobs.DiagnosticsScheduler;
import com.emc.storageos.systemservices.impl.property.PropertyManager;
import com.emc.storageos.systemservices.impl.recovery.RecoveryManager;
import com.emc.storageos.systemservices.impl.security.SecretsManager;
import com.emc.storageos.systemservices.impl.upgrade.ClusterAddressPoller;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.RemoteRepository;
import com.emc.storageos.systemservices.impl.upgrade.UpgradeManager;
import com.emc.storageos.systemservices.impl.upgrade.beans.SoftwareUpdate;
import com.emc.storageos.systemservices.impl.util.DrPostFailoverDBCheckHandler;
import com.emc.storageos.systemservices.impl.util.DrSiteNetworkMonitor;
import com.emc.storageos.systemservices.impl.util.MailHandler;
import com.emc.storageos.systemservices.impl.vdc.VdcManager;

/**
 * Default SysSvc implementation - starts/stops REST service
 */
public class SysSvcImpl extends AbstractSecuredWebServer implements SysSvc {
    private static final Logger log = LoggerFactory.getLogger(SysSvcImpl.class);

    private UpgradeManager _upgradeMgr;
    private InternalApiSignatureKeyGenerator _keyGenerator;
    private Thread _upgradeManagerThread = null;
    private Thread _secretsManagerThread = null;
    private Thread _propertyManagerThread = null;
    private Thread _vdcManagerThread = null;
    private Thread _ipreconfigManagerThread = null;
    private Thread _drNetworkMonitorThread = null;
    private int _timeout;
    private SoftwareUpdate _softwareUpdate;

    @Autowired
    private MailHandler _mailHandler;

    @Autowired
    private SecretsManager _secretsMgr;

    @Autowired
    private PropertyManager _propertyMgr;

    @Autowired
    private VdcManager _vdcMgr;

    @Autowired
    private IpReconfigManager _ipreconfigMgr;

    @Autowired
    private ServiceBeacon _svcBeacon;

    @Autowired
    private CoordinatorClientExt _coordinator;

    @Autowired
    private RecoveryManager _recoveryMgr;

    @Autowired
    // used by data node to poll the ip address change of controller cluster
    private ClusterAddressPoller _clusterPoller;

    @Autowired
    private DrSiteNetworkMonitor _drSiteNetworkMonitor;

    @Autowired
    private DiagnosticsScheduler diagnosticsScheduler;
    
    @Autowired
    private DrPostFailoverDBCheckHandler drPostFailoverDBCheckHandler;

    public void setUpgradeManager(UpgradeManager upgradeMgr) {
        _upgradeMgr = upgradeMgr;
    }

    public void setMailHandler(MailHandler mailHandler) {
        _mailHandler = mailHandler;
    }

    public void setClusterPoller(ClusterAddressPoller _clusterPoller) {
        this._clusterPoller = _clusterPoller;
    }

    /**
     * Set SysClient timeout
     * 
     * @param timeout
     */
    public void setSysClientTimeout(int timeout) {
        _timeout = timeout;
    }

    /**
     * Instantiate SoftwareUpdate bean
     *
     */
    public void setSoftwareUpdate(SoftwareUpdate softwareUpdate) {
        _softwareUpdate = softwareUpdate;
    }

    /**
     * Set the key generator
     * 
     */
    public void setKeyGenerator(InternalApiSignatureKeyGenerator keyGenerator) {
        _keyGenerator = keyGenerator;
    }

    private void startUpgradeManager() {
        _upgradeManagerThread = new Thread(_upgradeMgr);
        _upgradeManagerThread.setName("UpgradeManager");
        _upgradeManagerThread.start();
    }

    private void startSecretsManager() {
        _secretsManagerThread = new Thread(_secretsMgr);
        _secretsManagerThread.setName("SecretsManager");
        _secretsManagerThread.start();
    }

    private void startPropertyManager() {
        _propertyManagerThread = new Thread(_propertyMgr);
        _propertyManagerThread.setName("PropertyManager");
        _propertyManagerThread.start();
    }

    private void startVdcManager() {
        _vdcManagerThread = new Thread(_vdcMgr);
        _vdcManagerThread.setName("VdcManager");
        _vdcManagerThread.start();
    }

    private void startNewVersionCheck() {
        if (_coordinator.isControlNode()) {
            RemoteRepository.setCoordinator(_coordinator);
            RemoteRepository.startRemoteRepositoryCacheUpdate();
        }
    }

    private void stopNewVersionCheck() {
        if (_coordinator.isControlNode()) {
            RemoteRepository.stopRemoteRepositoryCacheUpdate();
        }
    }

    private void initSysClientFactory() {
        SysClientFactory.setKeyGenerator(_keyGenerator);
        SysClientFactory.setTimeout(_timeout);
        SysClientFactory.init();
    }

    private void startIpReconfigManager() {
        _ipreconfigManagerThread = new Thread(_ipreconfigMgr);
        _ipreconfigManagerThread.setName("IpReconfigManager");
        _ipreconfigManagerThread.start();
    }

    private void startSystemAudit(DbClient dbclient) {
        SystemAudit sysAudit = new SystemAudit(dbclient, _coordinator.getCoordinatorClient());
        Thread t = new Thread(sysAudit);
        t.start();
    }

    private void startNetworkMonitor() {
        _drNetworkMonitorThread = new Thread(_drSiteNetworkMonitor);
        _drNetworkMonitorThread.setName("DrSiteNetworkMonitor");
        _drNetworkMonitorThread.start();
    }

    private void startDiagnosticsScheduler() {
        diagnosticsScheduler.start();
    }

    @Override
    public void start() throws Exception {
        if (_app != null) {
            
            initThreadUncaughtExceptionHandler();
            
            initServer();
            initSysClientFactory();
            _server.start();

            // only data node needs to poll the cluster's address change
            if (!_coordinator.isControlNode()) {
                _clusterPoller.start();
            }

            startNewVersionCheck();
            startUpgradeManager();
            startSecretsManager();
            startIpReconfigManager();
            
            //config cassandra as client mode to avoid load yaml file
            Config.setClientMode(true);
            
            DrUtil drUtil = _coordinator.getDrUtil();
            if (drUtil.isActiveSite()) {
                _recoveryMgr.init();
                startSystemAudit(_dbClient);
            }
            _svcBeacon.start();
            // start property manager and vdc manager after beacon is registered
            // since they would update beacon
            startPropertyManager();
            startVdcManager();

            if (drUtil.isActiveSite()) {
                startNetworkMonitor();
            }

            startDiagnosticsScheduler();
            
            drPostFailoverDBCheckHandler.run();
        } else {
            throw new Exception("No app found.");
        }
    }

    @Override
    public void stop() throws Exception {
        _upgradeMgr.stop();
        _secretsMgr.stop();
        _propertyMgr.stop();
        _vdcMgr.stop();
        stopNewVersionCheck();
        _server.stop();
    }
    
    private void initThreadUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                log.error("Current thread throws uncaught exception", e);
            }
        });
        
    }
}

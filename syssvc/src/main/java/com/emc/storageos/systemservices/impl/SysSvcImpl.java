/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.impl;

import com.emc.storageos.systemservices.impl.ipreconfig.IpReconfigManager;
import com.emc.storageos.systemservices.impl.property.PropertyManager;
import com.emc.storageos.systemservices.impl.security.SecretsManager;
import com.emc.storageos.systemservices.impl.security.SshConfigurator;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.security.AbstractSecuredWebServer;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.upgrade.ClusterAddressPoller;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.RemoteRepository;
import com.emc.storageos.systemservices.impl.upgrade.UpgradeManager;
import com.emc.storageos.systemservices.impl.recovery.RecoveryManager;
import com.emc.storageos.coordinator.client.beacon.ServiceBeacon;
import com.emc.storageos.systemservices.SysSvc;
import com.emc.storageos.systemservices.impl.audit.SystemAudit;
import com.emc.storageos.db.client.DbClient;

/**
 * Default SysSvc implementation - starts/stops REST service
 */
public class SysSvcImpl extends AbstractSecuredWebServer implements SysSvc {
    private UpgradeManager _upgradeMgr;
    private InternalApiSignatureKeyGenerator _keyGenerator;
    private Thread _upgradeManagerThread = null;
    private Thread _secretsManagerThread = null;
    private Thread _propertyManagerThread = null;
    private Thread _ipreconfigManagerThread = null;
    private int _timeout;

    @Autowired
    private SecretsManager _secretsMgr;

    @Autowired
    private PropertyManager _propertyMgr;

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

    public void setUpgradeManager(UpgradeManager upgradeMgr) {
        _upgradeMgr = upgradeMgr;
    }
    
    public void setClusterPoller(ClusterAddressPoller _clusterPoller) {
        this._clusterPoller = _clusterPoller;
    }

    /**
     * Set SysClient timeout
     * @param timeout
     */
    public void setSysClientTimeout(int timeout) {
        _timeout = timeout;
    }

    /**
     * Set the key generator
     *
     */
    public void setKeyGenerator(InternalApiSignatureKeyGenerator keyGenerator) {
        _keyGenerator = keyGenerator;
    }

    private void startUpgradeManager() {
        _upgradeManagerThread =  new Thread(_upgradeMgr);
        _upgradeManagerThread.setName("UpgradeManager");
        _upgradeManagerThread.start();
    }

    private void startSecretsManager() {
        _secretsManagerThread =  new Thread(_secretsMgr);
        _secretsManagerThread.setName("SecretsManager");
        _secretsManagerThread.start();
    }

    private void startPropertyManager() {
        _propertyManagerThread =  new Thread(_propertyMgr);
        _propertyManagerThread.setName("PropertyManager");
        _propertyManagerThread.start();
    }

    private void startNewVersionCheck() {
        if(_coordinator.isControlNode()) {
            RemoteRepository.setCoordinator(_coordinator);
            RemoteRepository.startRemoteRepositoryCacheUpdate();
        }
    }
    
    private void stopNewVersionCheck() {
        if(_coordinator.isControlNode()) {
            RemoteRepository.stopRemoteRepositoryCacheUpdate();
        }
    }
    
    private void initSysClientFactory() {
        SysClientFactory.setKeyGenerator(_keyGenerator);
        SysClientFactory.setTimeout(_timeout);
        SysClientFactory.init();
    }

    private void startIpReconfigManager() {
        _ipreconfigManagerThread =  new Thread(_ipreconfigMgr);
        _ipreconfigManagerThread.setName("IpReconfigManager");
        _ipreconfigManagerThread.start();
    }

    private void startSystemAudit(DbClient dbclient) {
        SystemAudit sysAudit = new SystemAudit(dbclient);
        Thread t = new Thread(sysAudit);
        t.start();
    }

    @Override
    public void start() throws Exception {
        if (_app != null) {
            initServer();
            initSysClientFactory();
            _server.start();
            
            // only data node needs to poll the cluster's address change
            if(!_coordinator.isControlNode()) {
            	_clusterPoller.start();
            }
            startNewVersionCheck();
            startUpgradeManager();
            startSecretsManager();
            startPropertyManager();
            startIpReconfigManager();
            _recoveryMgr.init();
            startSystemAudit(_dbClient);
            _svcBeacon.start();            
        } else {
            throw new Exception("No app found.");
        }
    }

    @Override
    public void stop() throws Exception {
        _upgradeMgr.stop();
        _secretsMgr.stop();
        _propertyMgr.stop();
        stopNewVersionCheck();
        _server.stop();        
    }
}

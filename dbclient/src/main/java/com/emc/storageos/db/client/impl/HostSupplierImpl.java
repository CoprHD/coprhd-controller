/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.google.common.base.Supplier;
import com.netflix.astyanax.connectionpool.Host;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

/**
 * Plugs into astyanax connection pool for host discovery. It's implemented using
 * coordinator db service information
 */
public class HostSupplierImpl implements Supplier<List<Host>> {
    private static final Logger _log = LoggerFactory.getLogger(HostSupplierImpl.class);
    private static final int SLEEP_BETWEEN_RETRY = 10000; // 10 sec
    private static final int NUM_RETRY_COUNT = 60; // total wait time 10 minutes

    private CoordinatorClient _coordinator;
    private String _version;
    private String dbSvcName;

    /**
     * Coordinator dependency
     * 
     * @param coordinator
     */
    public void setCoordinatorClient(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }
    
    public CoordinatorClient getCoordinatorClient() {
        return _coordinator;
    }
    
    /**
     * DB client version in use
     * 
     * @param version
     */
    public void setDbClientVersion(String version) {
        _version = version;
    }

    public String getDbClientVersion() {
        return _version;
    }
    
    @Override
    public List<Host> get() {
        int sleepDuration = SLEEP_BETWEEN_RETRY;
        List<Host> hosts = null;
        for (int i = 1; i <= NUM_RETRY_COUNT; i++) {
            try {
                hosts = internalGet();
            } catch (RuntimeException ignore) {
                _log.warn("ignore get host fail:{}", ignore.getMessage());
            }

            if ((hosts == null) || (hosts.isEmpty())) {
                _log.warn("hostsupplier is empty. May be dbsvc hasn't started yet. waiting for " + sleepDuration + " msec");
                try {
                    Thread.sleep(sleepDuration);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                }
            } else {
                return hosts;
            }
        }
        return hosts;
    }

    public List<Host> internalGet() {
        try {
            _log.debug("getting hosts for " + dbSvcName + "; version = " + _version);
            boolean isGeodb = Constants.GEODBSVC_NAME.equals(dbSvcName);
            List<Service> service = _coordinator.locateAllServices(dbSvcName, _version, (String) null, null);
            List<Host> hostList = new ArrayList<Host>(service.size());

            for (int i = 0; i < service.size(); i++) {
                Service svc = service.get(i);
                if (isGeodb && isDbReinitializing(svc)) {
                    _log.debug("Ignore host {} because its geodb is reinitialzing", svc.getId());
                    continue;
                }
                URI hostUri = svc.getEndpoint();
                _log.debug("Found " + svc.getName() + "; host = " + hostUri.getHost() + "; port = " + hostUri.getPort());
                hostList.add(new Host(String.format(
                        "%1$s:%2$d", hostUri.getHost(), hostUri.getPort()), hostUri.getPort()));
            }
            _log.debug("dbsvc endpoint refreshed");
            return hostList;
        } catch (RetryableCoordinatorException e) {
            _log.warn("no dbsvc instance running. Coordinator exception message: {}", e.getMessage());
        } catch (Exception e) {
            _log.error("dbsvc lookup failure", e);
        }
        return Collections.emptyList();
    }

    private boolean isDbReinitializing(Service serviceInfo) {
        String configKind = _coordinator.getDbConfigPath(serviceInfo.getName());
        Configuration config = _coordinator.queryConfiguration(_coordinator.getSiteId(), configKind, serviceInfo.getId());
        String value = config.getConfig(Constants.REINIT_DB);
        return (value != null && Boolean.parseBoolean(value));
    }

    public String getDbSvcName() {
        return dbSvcName;
    }

    public void setDbSvcName(String dbSvcName) {
        this.dbSvcName = dbSvcName;
    }
}

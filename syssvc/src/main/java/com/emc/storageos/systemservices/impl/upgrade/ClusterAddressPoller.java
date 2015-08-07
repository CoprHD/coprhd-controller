/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.upgrade;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.systemservices.exceptions.LocalRepositoryException;
import com.emc.storageos.systemservices.exceptions.SyssvcException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.services.util.Waiter;

/**
 * Invoked on data node only. It starts a scheduled thread to poll the controller cluster's ip address change
 * to determine whether the vip changed, or some cluster nodes's ip address get changed. If so, it automatically
 * update the data node of the latest ip address change. But if both vip and node ip address get changed, manual
 * intervention is required.
 */
public class ClusterAddressPoller implements Runnable {

    private static final Logger _log = LoggerFactory.getLogger(ClusterAddressPoller.class);

    // lag in seconds before polling thread starts
    private long pollStartLag;
    // poll interval in seconds
    private long pollInterval;

    static private final int DEFAULT_SVC_PORT = 9998;

    String ADDRESSV4_FORMAT = "network_%1$s_ipaddr";
    String ADDRESSV6_FORMAT = "network_%1$s_ipaddr6";

    @Autowired
    private CoordinatorClientExt _coordinator;

    @Autowired
    private LocalRepository _localRepository;

    private final Waiter _waiter = new Waiter();

    // cache the last successful sysClient which uses vip so in case data node lost
    // connection to controller's coordinator, ClusterAddressPoller can use loaded
    // signature keys to make internal api calls.
    SysClientFactory.SysClient _lastVipSysClient;

    /**
     * constructor
     */
    public ClusterAddressPoller() {
    }

    public long getPollStartLag() {
        return pollStartLag;
    }

    public void setPollStartLag(long pollStartLag) {
        this.pollStartLag = pollStartLag;
    }

    public long getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(long pollInterval) {
        this.pollInterval = pollInterval;
    }

    public CoordinatorClientExt getCoordinator() {
        return _coordinator;
    }

    public void setCoordinator(CoordinatorClientExt _coordinator) {
        this._coordinator = _coordinator;
    }

    public LocalRepository getLocalRepository() {
        return _localRepository;
    }

    public void setLocalRepository(LocalRepository _localRepository) {
        this._localRepository = _localRepository;
    }

    /**
     * Start polling thread
     */
    public void start() {
        new Thread(this).start();
    }

    /**
     * thread function
     */
    @Override
    public void run() {
        _waiter.sleep(pollStartLag);
        while (true) {
            try {
                getUpdatedDataNodeConfig();
            } catch (Exception e) {
                _log.error("getUpdatedDataNodeConfig, exception: {}", e);
            }
            _waiter.sleep(pollInterval);
        }
    }

    public void getUpdatedDataNodeConfig() throws Exception
    {
        if (getCoordinator().isControlNode()) {
            return;
        }
        boolean bConnectCoordinator = false;
        // check if lost connection to controller cluster's coordinator (nodes' address changed)
        try {
            bConnectCoordinator = getCoordinator().isConnected();
        } catch (Exception e) {
            bConnectCoordinator = false;
            _log.error("Cannot access controller's coordinator: " + e.getMessage());
        }

        // if cannot connect to controller cluster's coordinator
        if (!bConnectCoordinator) {
            if (_lastVipSysClient == null) {
                _log.error("Cannot connect to controller via cached vip or coordinator");
                throw SyssvcException.syssvcExceptions.failConnectControllerError(
                        "Cannot connect to controller via coordinator or vip");
            }

            PropertyInfoRestRep rep = null;
            try {
                rep = _lastVipSysClient.post(SysClientFactory.URI_GET_PROPERTIES,
                        PropertyInfoRestRep.class, "OVF");
            } catch (Exception e) {
                // now cannot access vip as well as cluster's coordinator
                _log.error("Cannot connect to controller via coordinator, failed accessing last vip {}, {}",
                        _lastVipSysClient.getServiceURI(), e);
                throw e;
            }

            // try to get props cached locally
            PropertyInfoExt localProps = null;
            try {
                localProps = getLocalRepository().getControllerOvfProperties();
            } catch (LocalRepositoryException e) {
                _log.error("Failed to get controller properties from local repository");
                throw e;
            }

            // Check if controller nodes' address changed
            Map<String, String> nodeDiffProps = checkNodeAddressDiff(localProps, rep);
            if (nodeDiffProps.size() > 0) {
                try {
                    setLocalRepoControllerProps(nodeDiffProps);

                    _log.info("rebooting to get updated cluster addresses");
                    getLocalRepository().reboot();
                } catch (Exception e) {
                    _log.error("Reboot failed, ", e);
                    throw e;
                }
            }
            return;
        }

        // Now data node can connect to cluster's coordinator, check if vip changed or not
        PropertyInfoRestRep rep = null;
        if (_lastVipSysClient != null) {
            try {
                rep = _lastVipSysClient.post(SysClientFactory.URI_GET_PROPERTIES,
                        PropertyInfoRestRep.class, "OVF");
            } catch (Exception e) {
                rep = null;
                // now cannot access vip as well as cluster's coordinator
                _log.error("Failed accessing last vip {}, {}", _lastVipSysClient.getServiceURI(), e);
            }
        }
        PropertyInfoExt localProps = null;

        // get controller properties cached locally
        try {
            localProps = getLocalRepository().getControllerOvfProperties();

        } catch (LocalRepositoryException e) {
            _log.error("Failed to retrive controller properties from local repository");
            throw e;
        }

        // Try vip cached locally to get controller properties using internal api
        if (rep == null) {
            String vipURL = getUrl(localProps.getProperty("network_vip"),
                    localProps.getProperty("network_vip6"));

            SysClientFactory.SysClient sysClient = SysClientFactory.getSysClient(URI
                    .create(vipURL));
            try {
                rep = sysClient.post(SysClientFactory.URI_GET_PROPERTIES,
                        PropertyInfoRestRep.class, "OVF");
            } catch (Exception e) {
                _log.error("Failed accessing vip {}, {}", vipURL, e);
            }
        }

        // When vip has changed, try to use node addresses cached locally to
        // get properties
        if (rep == null) {
            rep = getControllerPropsFromNode(localProps);
        }

        if (rep == null) {
            _log.error("Failed to get controller properties from cluster");
            throw SyssvcException.syssvcExceptions.failConnectControllerError(
                    "Cannot connect to controller via node addresses or vip");
        }

        // After getting properties from controller, check and compare if vip has changed.
        // If vip change is found, update vip in local cache.
        Map<String, String> diffProps = checkVipDiff(localProps, rep);
        if (diffProps.size() > 0) {
            try {
                setLocalRepoControllerProps(diffProps);
                _log.error("Successfully set vip in local repository");
            } catch (LocalRepositoryException e) {
                _log.error("Failed to set vip in local repository");
                throw e;
            }
        } else {
            _log.info("vip not changed");
        }

        // Cache the last known valid vip client, whether vip changed or not
        // so that it can be used for the next poll interval
        SysClientFactory.SysClient sysClient = SysClientFactory.getSysClient(URI
                .create(getUrl(rep.getProperty("network_vip"),
                        rep.getProperty("network_vip6"))));

        PropertyInfoRestRep propRep = null;
        try {
            propRep = sysClient.post(SysClientFactory.URI_GET_PROPERTIES,
                    PropertyInfoRestRep.class, "OVF");
            if (propRep != null) {
                // cache the validated vip client where secret key is cached.
                // so that if next poll cycle data node cannot connect to coordinator
                // it can use this vip client to invoke internal api
                _lastVipSysClient = sysClient;
            }
        } catch (Exception e) {
            _log.error("Failed accessing vip {}, {}", _lastVipSysClient.getServiceURI(), e);
        }

        // also need check individual controller nodes to see if any address changed
        // because in a cluster though some nodes address changed, data node may still
        // can access to coordinator if majority nodes of a zookeeper ensemble remains
        // If controller node address change detected, restart the node.
        Map<String, String> nodeDiffProps = checkNodeAddressDiff(localProps, rep);
        if (nodeDiffProps.size() > 0) {
            try {
                setLocalRepoControllerProps(nodeDiffProps);

                _log.info("rebooting to get updated cluster addresses");
                getLocalRepository().reboot();
            } catch (Exception e) {
                _log.error("Reboot failed, ", e);
                throw e;
            }
        }
    }

    /**
     * Construct vip url
     * 
     * @param vipAddrV4 ipv4 address
     * @param vipAddrV6 ipV4 address
     * @return vip url for internal api
     */
    public String getUrl(String vipAddrV4, String vipAddrV6) {
        if (!vipAddrV4.equals("0.0.0.0") || vipAddrV4.length() == 0) {
            return String.format(SysClientFactory.BASE_URL_FORMAT,
                    vipAddrV4, DEFAULT_SVC_PORT);
        }
        return String.format(SysClientFactory.BASE_URL_FORMAT,
                "[" + vipAddrV6 + "]", DEFAULT_SVC_PORT);
    }

    /**
     * Check if controller node address changed or not
     * 
     * @param localProps controller properties cached locally
     * @param rep controller properties acquired from controller using internal api
     * @return true if any node address change is detected
     */
    private Map<String, String> checkNodeAddressDiff(PropertyInfoExt localProps, PropertyInfoRestRep rep) {

        int nodeCount = Integer.parseInt(localProps.getProperty("node_count"));
        Map<String, String> diffProps = new HashMap<String, String>();
        // compare if any node address changed, include both ipv4 and ipv4 address
        for (int i = 1; i <= nodeCount; i++) {
            String address = String.format(ADDRESSV4_FORMAT, i);
            if (!rep.getProperty(address).equals(localProps.getProperty(address))) {
                diffProps.put(address, rep.getProperty(address));
            }
            address = String.format(ADDRESSV6_FORMAT, i);
            if (!rep.getProperty(address).equals(localProps.getProperty(address))) {
                diffProps.put(address, rep.getProperty(address));
            }

        }
        return diffProps;
    }

    /**
     * Get controller properties using internal api from controller nodes.
     * This method is invoked when vip is changed
     * 
     * @param localProps config properties cached locally on data node
     * @return
     */
    private PropertyInfoRestRep getControllerPropsFromNode(PropertyInfoExt localProps) {
        int nodeCount = Integer.parseInt(localProps.getProperty("node_count"));

        PropertyInfoRestRep rep = null;
        for (int i = 1; i <= nodeCount; i++) {
            String baseNodeUrl = getUrl(localProps.getProperty(String.format(ADDRESSV4_FORMAT, i)),
                    localProps.getProperty(String.format(ADDRESSV6_FORMAT, i)));
            SysClientFactory.SysClient sysClient = SysClientFactory.getSysClient(URI
                    .create(baseNodeUrl));
            try {
                rep = sysClient.post(SysClientFactory.URI_GET_PROPERTIES,
                        PropertyInfoRestRep.class, "OVF");
                break;
            } catch (Exception e) {
                // now cannot access vip as well as cluster's coordinator
                _log.error("Failed accessing url {}, {}", baseNodeUrl, e);
                rep = null;
                continue;
            }
        }
        return rep;
    }

    /**
     * Check if vip changed comparing locally cached vip vs vip get from controller
     * Diff
     * 
     * @param localProps locally cached controller properties
     * @param rep properties queried from controller using internal api
     */
    private Map<String, String> checkVipDiff(PropertyInfoExt localProps, PropertyInfoRestRep rep) {
        String vipAddrV4 = rep.getProperty("network_vip");
        String vipAddrV6 = rep.getProperty("network_vip6");
        Map<String, String> diffProps = new HashMap<String, String>();
        if (!vipAddrV4.equals(localProps.getProperty("network_vip"))) {
            diffProps.put("network_vip", vipAddrV4);
            _log.warn("Detected changed vip. remote vip: {}, local repo vip: {}",
                    vipAddrV4,
                    localProps.getProperty("network_vip"));
        }
        if (!vipAddrV6.equals(localProps.getProperty("network_vip6"))) {
            diffProps.put("network_vip6", vipAddrV6);
            _log.warn("Detected changed vip. remote vip: {}, local repo vip: {}", vipAddrV6,
                    localProps.getProperty("network_vip6"));
        }
        return diffProps;
    }

    /**
     * Set controller properties in local repository
     * 
     * @param props properties to set
     * @throws LocalRepositoryException
     */
    private void setLocalRepoControllerProps(Map<String, String> props) throws LocalRepositoryException {
        PropertyInfoExt controllerProps = getLocalRepository().getControllerOvfProperties();
        controllerProps.removeProperties(props.keySet());
        controllerProps.addProperties(props);
        getLocalRepository().setControllerOvfProperties(controllerProps);
    }
}
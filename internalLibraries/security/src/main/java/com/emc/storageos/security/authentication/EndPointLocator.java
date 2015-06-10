/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.authentication;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.security.exceptions.SecurityException;

/**
 * 
 */
public class EndPointLocator {

    private final Logger _log = LoggerFactory.getLogger(getClass());

    private final static String OVF_NETWORK_VIP = "network_vip";

    private static String _cachedNetworkVip = null;
    private final static String NETWORK_VIP_DEFAULT = "0.0.0.0";

    @Autowired
    private CoordinatorClient _coordinator;

    private ServiceLocatorInfo _svcInfo;

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    protected void setServiceLocatorInfo(ServiceLocatorInfo serviceLocatorInfo) {
        _svcInfo = serviceLocatorInfo;
    }

    /**
     * Queries the coordinator for a list of Service info objects for authsvc
     * 
     * @return a list of services.
     */
    private List<Service> getServiceInfoListInternal() {
        _log.debug("Retrieving authsvc service info from coordinator service");
        List<Service> services = null;
        try {
            services =
                    _coordinator.locateAllServices(_svcInfo.getServiceName(),
                            _svcInfo.getServiceVersion(), null, null);
            if (_log.isDebugEnabled()) {
                for (Service s : services) {
                    _log.debug("Service name: {}", s.getName());
                    _log.debug("Service version: {}", s.getVersion());
                    _log.debug("Service id: {}", s.getId());
                    _log.debug("Service end point: {}", s.getEndpoint().toString());
                }
            }
        } catch (Exception ex) {
            _log.error("Exception while retrieving authsvc service information: {}",
                    ex.getStackTrace());
            throw SecurityException.retryables.requiredServiceUnvailable(
                    _svcInfo.getServiceName(), ex);
        }
        if (services == null || services.isEmpty()) {
            throw SecurityException.retryables.requiredServiceUnvailable(_svcInfo
                    .getServiceName());
        }
        return services;
    }

    /**
     * Extracts a list of endpoints from a list of service info
     * 
     * @return
     */
    public List<URI> getServiceEndpointList() {
        // get a list of the endpoints on the cluster.
        ArrayList<URI> toReturn = new ArrayList<URI>();
        List<Service> services = getServiceInfoListInternal();
        for (Service s : services) {
            toReturn.add(s.getEndpoint());
        }

        // if not, return the list we got from the coordinator.
        return toReturn;
    }

    /**
     * Get one available end point for the requested service. If the network_vip ovf
     * property is set, an endpoint based on that IP address is returned. If not, then an
     * endpoint based on the first entry from the list returned from the coordinator is
     * returned
     * 
     * @return
     */
    public URI getAnEndpoint() {
        List<URI> endpoints = getServiceEndpointList();
        if (endpoints == null || endpoints.size() < 1) {
            throw SecurityException.retryables.requiredServiceUnvailable(_svcInfo
                    .getServiceName());
        }

        // extract an end point
        URI endpoint = endpoints.get(0);
        _log.debug(
                "Endpoint set {}.  Using this value instead of querying the coordinator service",
                endpoint.toString());
        return endpoint;
    }

    /**
     * Consults the cached value of network_vip. If set, returns it. If not set, gets it
     * from coordinator and update cache.
     * 
     * @return
     */
    private String queryCachedNetworkVip() {
        String networkVipToReturn = null;
        if (_cachedNetworkVip == null) {
            _log.debug("No cached value found for network_vip property");
            PropertyInfo props = null;
            try {
                props = _coordinator.getPropertyInfo();
            } catch (Exception e) {
                _log.error("Could not query for network_vip property", e);
                return null;
            }
            if (props == null) {
                _log.error("Query for network_vip property returned null");
                return null;
            }
            networkVipToReturn = props.getProperty(OVF_NETWORK_VIP);
            synchronized (this) {
                _cachedNetworkVip = networkVipToReturn;
            }
            if (networkVipToReturn == null || networkVipToReturn.equals("")) {
                _log.debug("Cluster endpoint value is not supplied");
                return null;
            }
        } else {
            _log.debug("Cached value found for network_vip.  Reusing {}",
                    _cachedNetworkVip);
            networkVipToReturn = _cachedNetworkVip;
        }
        return networkVipToReturn;
    }

}

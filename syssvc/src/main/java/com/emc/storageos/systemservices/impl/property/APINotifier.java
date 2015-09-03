/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */


package com.emc.storageos.systemservices.impl.property;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.security.authentication.AuthSvcEndPointLocator;
import com.emc.storageos.security.authentication.AuthSvcInternalApiClientIterator;
import com.sun.jersey.api.client.ClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

/**
 * when property changes, notify service(s) on other nodes to reload it from zk.
 */
public class APINotifier {

    private static final Logger _log = LoggerFactory.getLogger(APINotifier.class);
    private static final URI _URI_AUTH_RELOAD = URI.create("/internal/reloadAuthsvcProperty");


    private CoordinatorClient _coordinator;
    public void setCoordinator(CoordinatorClient locator) {
        _coordinator = locator;
    }

    @Autowired
    private AuthSvcEndPointLocator _authSvcEndPointLocator;

    /**
     * Call the internode URI on all authSvc endpoints to reload
     */
    public void notifyChangeToAuthsvc() {
        try {
            AuthSvcInternalApiClientIterator authSvcItr = new AuthSvcInternalApiClientIterator(_authSvcEndPointLocator, _coordinator);
            while (authSvcItr.hasNext()) {
                String endpoint = authSvcItr.peek().toString();
                _log.info("sending request to endpoint: " + endpoint);
                try {
                    ClientResponse response = authSvcItr.post(_URI_AUTH_RELOAD, null);
                    if (response.getStatus() != ClientResponse.Status.OK.getStatusCode()) {
                        _log.error("Failed to reload authN providers on endpoint {} response {}", endpoint, response.toString());
                    }
                } catch (Exception e) {
                    _log.error("Caught exception trying to reload an authsvc on {} continuing", endpoint, e);
                }
            }
        } catch (CoordinatorException e) {
            _log.error("Caught coordinator exception trying to find an authsvc endpoint", e);
        }
    }
}

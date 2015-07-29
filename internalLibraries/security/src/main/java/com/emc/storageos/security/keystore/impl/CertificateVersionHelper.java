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
package com.emc.storageos.security.keystore.impl;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.authentication.AuthSvcInternalApiClientIterator;
import com.emc.storageos.security.authentication.SysSvcEndPointLocator;
import com.sun.jersey.api.client.ClientResponse;

/**
 * a helper class to set certificate related properties
 */
public class CertificateVersionHelper {
    private static final Logger log = LoggerFactory.getLogger(CertificateVersionHelper.class);
    private static final URI URI_INCREMENT_CERTIFICATE_VERSION = URI
            .create("/config/internal/certificate-version");
    private static final int MAX_CONFIG_RETRIES = 5;
    private SysSvcEndPointLocator sysSvcEndPointLocator;
    private CoordinatorClient coordinator;

    public CertificateVersionHelper() {
    }

    public CertificateVersionHelper(CoordinatorClient coordinator) {
        this.setCoordinator(coordinator);
        sysSvcEndPointLocator = new SysSvcEndPointLocator();
        sysSvcEndPointLocator.setCoordinator(coordinator);
    }

    /**
     * @param coordinator
     *            the coordinator to set
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * @param sysSvcEndPointLocator the sysSvcEndPointLocator to set
     */
    public void setSysSvcEndPointLocator(SysSvcEndPointLocator sysSvcEndPointLocator) {
        this.sysSvcEndPointLocator = sysSvcEndPointLocator;
    }

    public boolean updateCertificateVersion() {
        int attempts = 0;
        while (attempts < MAX_CONFIG_RETRIES) {
            log.debug("Config attempt {}", ++attempts);
            AuthSvcInternalApiClientIterator sysSvcClientItr =
                    new AuthSvcInternalApiClientIterator(sysSvcEndPointLocator,
                            coordinator);
            if (sysSvcClientItr.hasNext()) {
                final ClientResponse response =
                        sysSvcClientItr.put(URI_INCREMENT_CERTIFICATE_VERSION, null);
                final int status = response.getStatus();

                if (status == ClientResponse.Status.OK.getStatusCode()
                        || status == ClientResponse.Status.ACCEPTED.getStatusCode()) {
                    return true;
                } else {
                    log.debug("Failed with status " + status
                            + " to set certificate version.");
                }
            }
        }
        return false;
    }

}

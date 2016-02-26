/*
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.emc.storageos.keystone.restapi.utils;

import com.emc.storageos.keystone.restapi.KeystoneApiClient;
import com.emc.storageos.keystone.restapi.KeystoneRestClientFactory;
import com.emc.storageos.keystone.restapi.model.response.*;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;

/**
 * Keystone API Utils class.
 */
public class KeystoneUtils {

    private static final Logger _log = LoggerFactory.getLogger(KeystoneUtils.class);
    public static final String OPENSTACK_CINDER_V2_NAME = "cinderv2";
    public static final String OPENSTACK_CINDER_V1_NAME = "cinder";
    public static final String OPENSTACK_TENANT_ID = "tenant_id";
    public static final String OPENSTACK_DEFAULT_REGION = "RegionOne";

    private KeystoneRestClientFactory _keystoneApiFactory;

    public void setKeystoneFactory(KeystoneRestClientFactory factory) {
        this._keystoneApiFactory = factory;
    }

    /**
     * Delete endpoint for the service with given ID.
     *
     * @param keystoneApi KeystoneApiClient.
     * @param serviceId OpenStack service ID.
     */
    public void deleteKeystoneEndpoint(KeystoneApiClient keystoneApi, String serviceId) {
        _log.debug("START - deleteKeystoneEndpoint");

        if (serviceId == null) {
            _log.error("serviceId is null");
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Service id");
        }

        // Get Keystone endpoints from Keystone API.
        EndpointResponse endpoints = keystoneApi.getKeystoneEndpoints();
        // Find endpoint to delete.
        EndpointV2 endpointToDelete = findEndpoint(endpoints, serviceId);
        // Do not execute delete call when endpoint does not exist.
        if (endpointToDelete != null) {
            // Delete endpoint using Keystone API.
            keystoneApi.deleteKeystoneEndpoint(endpointToDelete.getId());
        }
        _log.debug("END - deleteKeystoneEndpoint");
    }

    /**
     * Retrieves OpenStack endpoint related to given service ID.
     *
     * @param serviceId Service ID.
     * @return OpenStack endpoint for the given service ID.
     */
    public EndpointV2 findEndpoint(EndpointResponse response, String serviceId) {
        _log.debug("START - findEndpoint");

        if (serviceId == null) {
            _log.error("serviceId is null");
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Service id");
        }

        for (EndpointV2 endpoint : response.getEndpoints()) {
            if (endpoint.getServiceId().equals(serviceId)) {
                _log.debug("END - findEndpoint");
                return endpoint;
            }
        }

        _log.warn("Missing endpoint for service {}", serviceId);
        // Return null if there is no endpoints for given service.
        return null;
    }

    /**
     * Retrieves OpenStack service ID with given service name.
     *
     * @param keystoneApi Keystone Api client.
     * @param serviceName Name of a service to retrieve.
     * @return ID of service with given name.
     */
    public String findServiceId(KeystoneApiClient keystoneApi, String serviceName) {
        _log.debug("START - findServiceId");

        if (serviceName == null) {
            _log.error("serviceName is null");
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Service name");
        }

        // Get Keystone services from Keystone API.
        ServiceResponse services = keystoneApi.getKeystoneServices();

        for (ServiceV2 service : services.getServices()) {
            if (service.getName().equals(serviceName)) {
                _log.debug("END - findServiceId");
                return service.getId();
            }
        }
        _log.warn("Missing service {}", serviceName);
        // Return null if service is missing.
        return null;
    }

    /**
     * Retrieves URI from server urls.
     *
     * @param serverUrls Set of strings representing server urls.
     * @return First URI from server urls param.
     */
    public URI retrieveUriFromServerUrls(Set<String> serverUrls) {
        URI authUri = null;
        for (String uri : serverUrls) {
            authUri = URI.create(uri);
            break; // There will be single URL only
        }
        return authUri;
    }

    /**
     * Get region for the service with given ID.
     *
     * @param keystoneApi KeystoneApiClient.
     * @param serviceId OpenStack service ID.
     */
    public String getRegionForService(KeystoneApiClient keystoneApi, String serviceId) {
        _log.debug("START - getRegionForService");

        if (serviceId == null) {
            _log.error("serviceId is null");
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Service id");
        }

        // Get Keystone endpoints from Keystone API.
        EndpointResponse endpoints = keystoneApi.getKeystoneEndpoints();
        // Find endpoint for the service.
        EndpointV2 endpoint = findEndpoint(endpoints, serviceId);
        // Return null if endpoint is null, otherwise return region name.
        if (endpoint != null) {
            _log.debug("END - getRegionForService");
            // Return region name.
            return endpoint.getRegion();
        }
        _log.warn("Endpoint missing for a service with ID: {}", serviceId);

        return null;
    }

    /**
     * Get Keystone API client.
     *
     * @param authUri URI pointing to Keystone server.
     * @param username OpenStack username.
     * @param usernamePassword OpenStack password.
     * @param tenantName OpenStack tenantname.
     * @return keystoneApi KeystoneApiClient.
     */
    public KeystoneApiClient getKeystoneApi(URI authUri, String username, String usernamePassword, String tenantName) {

        // Get Keystone API Client.
        KeystoneApiClient keystoneApi = (KeystoneApiClient) _keystoneApiFactory.getRESTClient(
                authUri, username, usernamePassword);
        keystoneApi.setTenantName(tenantName);

        return keystoneApi;
    }
}

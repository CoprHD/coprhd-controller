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
package com.emc.storageos.api.service.impl.resource.utils;

import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.model.tenant.TenantNamespaceInfo;

import com.emc.storageos.security.helpers.BaseServiceClient;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Internal API for maintaining mappings between tenant and namespace
 */
public class InternalTenantServiceClient extends BaseServiceClient {

    private static final String INTERNAL_TENANT_ROOT = "/internal/tenants";
    private static final String INTERNAL_TENANT_SET_NAMESPACE = INTERNAL_TENANT_ROOT + "/%s/namespace?name=%s";
    private static final String INTERNAL_TENANT_GET_NAMESPACE = INTERNAL_TENANT_ROOT + "/%s/namespace";
    private static final String INTERNAL_TENANT_UNSET_NAMESPACE = INTERNAL_TENANT_ROOT + "/%s/namespace";

    final private Logger _log = LoggerFactory
            .getLogger(InternalTenantServiceClient.class);

    /**
     * Client without target hosts
     */
    public InternalTenantServiceClient() {
    }

    /**
     * Client with specific host
     * 
     * @param server
     */
    public InternalTenantServiceClient(String server) {
        setServer(server);
    }

    /**
     * Make client associated with this api server host (IP)
     * 
     * @param server IP
     */
    @Override
    public void setServer(String server) {
        setServiceURI(URI.create("https://" + server + ":8443"));
    }

    /**
     * Set namespace mapping info for tenant or subtenant
     * 
     * @param tenantId the URN of a ViPR Tenant/Subtenant
     * @param namespace name of the target namespace the tenant will be mapped to
     * @return the updated Tenant/Subtenant instance
     */
    public TenantOrgRestRep setTenantNamespace(URI tenantId, String namespace) {
        String setNamespacePath = String.format(INTERNAL_TENANT_SET_NAMESPACE, tenantId.toString(), namespace);
        WebResource rRoot = createRequest(setNamespacePath);
        TenantOrgRestRep resp = null;
        try {
            resp = addSignature(rRoot)
                    .put(TenantOrgRestRep.class);
        } catch (UniformInterfaceException e) {
            _log.warn("could not attach namespace to tenant {}. Err:{}", tenantId, e);
        }
        return resp;
    }

    /**
     * Get namespace of a tenant or subtenant
     * 
     * @param tenantId the URN of a ViPR Tenant/Subtenant
     * @return the TenantNamespaceInfo
     */
    public TenantNamespaceInfo getTenantNamespace(URI tenantId) {
        String getNamespacePath = String.format(INTERNAL_TENANT_GET_NAMESPACE, tenantId.toString());
        WebResource rRoot = createRequest(getNamespacePath);
        TenantNamespaceInfo resp = null;
        try {
            resp = addSignature(rRoot)
                    .get(TenantNamespaceInfo.class);
        } catch (UniformInterfaceException e) {
            _log.warn("could not get namespace of tenant {}. Err:{}", tenantId, e);
        }
        return resp;
    }

    /**
     * Unset namespace mapping info from tenant or subtenant
     *
     * @param tenantId the URN of a ViPR Tenant/Subtenant
     * @prereq none
     * @brief unset namespace field
     * @return No data returned in response body
     */
    public ClientResponse unsetTenantNamespace(URI tenantId) {
        String unsetNamespacePath = String.format(INTERNAL_TENANT_UNSET_NAMESPACE, tenantId.toString());
        WebResource rRoot = createRequest(unsetNamespacePath);
        ClientResponse resp = null;
        try {
            resp = addSignature(rRoot)
                    .delete(ClientResponse.class);
        } catch (UniformInterfaceException e) {
            _log.warn("could not detach namespace from tenant {}. Err:{}", tenantId, e);
        }
        return resp;
    }
}

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
package com.emc.storageos.api.service.impl.resource;

import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.keystone.restapi.KeystoneApiClient;
import com.emc.storageos.keystone.restapi.model.response.TenantListRestResp;
import com.emc.storageos.keystone.restapi.model.response.TenantV2;
import com.emc.storageos.keystone.restapi.utils.KeystoneUtils;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * API for manipulating OpenStack Keystone service.
 */
@Path("/v2/keystone")
@DefaultPermissions(readRoles = { Role.SECURITY_ADMIN },
        writeRoles = { Role.SECURITY_ADMIN })
public class KeystoneService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(KeystoneService.class);

    private KeystoneUtils _keystoneUtils;

    public void setKeystoneUtils(KeystoneUtils keystoneUtils) {
        this._keystoneUtils = keystoneUtils;
    }

    /**
     * Get a list of OpenStack Tenants.
     * Uses data from Keystone Authentication Provider to connect Keystone and retrieve Tenants information.
     *
     * @brief Show OpenStack Tenants
     * @return OpenStack Tenants details
     * @see TenantListRestResp
     */
    @GET
    @Path("/tenants")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public TenantListRestResp getOpenstackTenants() {

        _log.debug("Keystone Service - getOpenstackTenants");

        StorageOSUser user = getUserFromContext();
        if (!_permissionsHelper.userHasGivenRoleInAnyTenant(user, Role.SECURITY_ADMIN, Role.TENANT_ADMIN)) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }

        AuthnProvider keystoneProvider = _keystoneUtils.getKeystoneProvider();

        // Get OpenStack Tenants only when Keystone Provider exists.
        if (keystoneProvider != null) {

            KeystoneApiClient keystoneApiClient = _keystoneUtils.getKeystoneApi(keystoneProvider.getManagerDN(),
                    keystoneProvider.getServerUrls(), keystoneProvider.getManagerPassword());

            List<TenantV2> OSTenantList = new ArrayList<>(Arrays.asList(keystoneApiClient.getKeystoneTenants().getTenants()));

            TenantListRestResp response = new TenantListRestResp();
            response.setOpenstack_tenants(OSTenantList);

            return response;
        }

        throw APIException.internalServerErrors.targetIsNullOrEmpty("Keystone Provider");

    }


    @Override
    protected DataObject queryResource(URI id) {
        return null;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return null;
    }
}

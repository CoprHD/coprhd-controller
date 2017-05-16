/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;

import com.emc.storageos.model.tenant.TenantResponse;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCodeException;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.model.RestLinkRep;

/**
 * class for resource to get current logged in tenant information
 */
@Path("/tenant")
public class TenantService extends ResourceService {
    @Context
    SecurityContext sc;

    /**
     * Get ID for caller's tenant.
     * The caller's ID is determined based on their token presented to the system during session initialization.
     * This is useful as a bootstrapping function to determine the ID to use for API calls such as creating a project or listing projects.
     * 
     * @prereq none
     * @brief Show id for caller's tenant
     * @return Tenant Identifier
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public TenantResponse getTenant() {
        StorageOSUser user = getUserFromContext();

        if (user.getTenantId() != null) {
            final TenantResponse resp = new TenantResponse();
            resp.setTenant(URI.create(user.getTenantId()));
            resp.setName(findTenantOrgName(resp.getTenant()));
            resp.setSelfLink(getSelfLink(resp.getTenant()));
            return resp;
        }
        throw APIException.badRequests.noTenantDefinedForUser(user == null ? "unknown" : user.getName());
    }

    private String findTenantOrgName(URI tenantId) {
        TenantOrg org = _permissionsHelper.getObjectById(tenantId, TenantOrg.class);
        if (org != null) {
            return org.getLabel();
        }

        throw new ServiceCodeException(ServiceCode.DBSVC_ENTITY_NOT_FOUND, "No Tenant Org defined for  " + tenantId, null);
    }

    private RestLinkRep getSelfLink(URI tenantId) {
        try {
            return new RestLinkRep("self", new URI("/tenants/" + tenantId.toString()));
        } catch (Exception ex) {
            return new RestLinkRep("self", null);
        }
    }
}

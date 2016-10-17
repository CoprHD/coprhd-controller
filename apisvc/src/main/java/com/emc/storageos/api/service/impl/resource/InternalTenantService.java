/*
 * Copyright 2008-2013 EMC Corporation
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

import static com.emc.storageos.api.mapper.DbObjectMapper.map;
import java.net.URI;
import java.util.List;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.model.project.ProjectElement;
import com.emc.storageos.model.project.ProjectParam;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.security.authorization.PermissionsKey;
import com.emc.storageos.security.authorization.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.model.tenant.*;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Internal API for maintenaning mappings between tenant and namespace
 */

@Path("/internal/tenants")
public class InternalTenantService extends ResourceService {
    private static final Logger _log = LoggerFactory.getLogger(InternalTenantService.class);

    private static final String ROOT = "root";

    @Autowired
    private TenantsService _tenantsService;

    private static final String EVENT_SERVICE_TYPE = "internalTenant";

    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Get tenant object from id
     * 
     * @param id the URN of a ViPR tenant
     * @return
     */
    private TenantOrg getTenantById(URI id, boolean checkInactive) {
        if (id == null) {
            return null;
        }
        TenantOrg org = _permissionsHelper.getObjectById(id, TenantOrg.class);
        ArgValidator.checkEntity(org, id, isIdEmbeddedInURL(id), checkInactive);
        return org;
    }

    /**
     * Set namespace mapping info for tenant or subtenant
     * 
     * @param id the URN of a ViPR Tenant/Subtenant
     * @param namespace name of the target namespace the tenant will be mapped to
     * @return the updated Tenant/Subtenant instance
     */
    @PUT
    @Path("/{id}/namespace")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public TenantOrgRestRep setTenantNamespace(@PathParam("id") URI id, @QueryParam("name") String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            throw APIException.badRequests.invalidParameterTenantNamespaceIsEmpty();
        }

        TenantOrg tenant = getTenantById(id, true);
        if (tenant.getNamespace() != null && !tenant.getNamespace().isEmpty()) {
            throw APIException.badRequests.tenantNamespaceMappingConflict(id.toString(), tenant.getNamespace());
        }

        tenant.setNamespace(namespace);
        _dbClient.persistObject(tenant);

        auditOp(OperationTypeEnum.SET_TENANT_NAMESPACE, true, null, id.toString(), tenant.getLabel(), namespace);
        return map(getTenantById(id, false));
    }

    /**
     * Get namespace attached with a tenant or subtenant
     * 
     * @param id the URN of a ViPR Tenant/Subtenant
     * @return the TenantNamespaceInfo
     */
    @GET
    @Path("/{id}/namespace")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public TenantNamespaceInfo getTenantNamespace(@PathParam("id") URI id) {
        String namespace = "";
        TenantOrg tenant = getTenantById(id, true);
        if (tenant.getNamespace() != null) {
            namespace = tenant.getNamespace();
        }

        TenantNamespaceInfo tenantNamespace = new TenantNamespaceInfo(namespace);
        auditOp(OperationTypeEnum.GET_TENANT_NAMESPACE, true, null, id.toString(), tenant.getLabel(), namespace);
        return tenantNamespace;
    }

    /**
     * Unset namespace mapping info from tenant or subtenant
     * 
     * @param id the URN of a ViPR Tenant/Subtenant
     * @prereq none
     * @brief unset namespace field
     * @return No data returned in response body
     */
    @DELETE
    @Path("/{id}/namespace")
    public Response unsetTenantNamespace(@PathParam("id") URI id) {
        TenantOrg tenant = getTenantById(id, true);
        String origNamespace = (tenant.getNamespace() == null) ? "" : tenant.getNamespace();

        tenant.setNamespace("");
        _dbClient.persistObject(tenant);

        auditOp(OperationTypeEnum.UNSET_TENANT_NAMESPACE, true, null, id.toString(), tenant.getLabel(), origNamespace);
        return Response.ok().build();
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public TenantOrgRestRep createTenant(TenantCreateParam param) {

        _log.debug("Create Tenant from internal call");

        URI rootId = _permissionsHelper.getRootTenant().getId();

        TenantOrg subtenant = new TenantOrg();
        subtenant.setId(URIUtil.createId(TenantOrg.class));
        subtenant.setParentTenant(new NamedURI(rootId, param.getLabel()));
        subtenant.setLabel(param.getLabel());
        subtenant.setDescription(param.getDescription());
        List<BasePermissionsHelper.UserMapping> userMappings = BasePermissionsHelper.UserMapping.fromParamList(param.getUserMappings());
        for (BasePermissionsHelper.UserMapping userMapping : userMappings) {
            userMapping.setDomain(userMapping.getDomain().trim());
            subtenant.addUserMapping(userMapping.getDomain(), userMapping.toString());
        }
        subtenant.addRole(new PermissionsKey(PermissionsKey.Type.SID,
                ROOT).toString(), Role.TENANT_ADMIN.toString());

        _dbClient.createObject(subtenant);

        auditOp(OperationTypeEnum.CREATE_TENANT, true, null, subtenant.getLabel(), rootId, subtenant.getId().toString());

        return map(subtenant);
    }

    @POST
    @Path("/{id}/projects")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ProjectElement createProject(@PathParam("id") URI id, ProjectParam param) {

        _log.debug("Create Project from internal call");

        TenantOrg owner = _dbClient.queryObject(TenantOrg.class, id);

        if (owner == null) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }

        return _tenantsService.createProject(owner.getId(), param, ROOT, owner.getId().toString());
    }
}

/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.toLink;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.HostMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.*;
import java.util.Map.Entry;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.mapper.functions.MapTenant;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.*;
import com.emc.storageos.model.auth.RoleAssignmentChanges;
import com.emc.storageos.model.auth.RoleAssignments;
import com.emc.storageos.model.host.HostCreateParam;
import com.emc.storageos.model.host.HostList;
import com.emc.storageos.model.host.cluster.ClusterCreateParam;
import com.emc.storageos.model.host.cluster.ClusterList;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterCreateParam;
import com.emc.storageos.model.host.vcenter.VcenterList;
import com.emc.storageos.model.project.ProjectElement;
import com.emc.storageos.model.project.ProjectList;
import com.emc.storageos.model.project.ProjectParam;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.model.tenant.*;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.*;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMapping;
import com.emc.storageos.security.validator.Validator;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

/**
 * Internal API for maintenaning mappings between tenant and namespace
 */

@Path("/internal/tenants")
public class InternalTenantService extends ResourceService {
    private static final Logger _log = LoggerFactory.getLogger(InternalTenantService.class);

    private static final String EVENT_SERVICE_TYPE = "internalTenant";

    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Get tenant object from id
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
     * @param id the URN of a ViPR Tenant/Subtenant
     * @param namespace name of the target namespace the tenant will be mapped to
     * @return the updated Tenant/Subtenant instance     
     */
    @PUT
    @Path("/{id}/namespace")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public TenantOrgRestRep setTenantNamespace(@PathParam("id") URI id, @QueryParam("name") String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            throw APIException.badRequests.invalidParameterTenantNamespaceIsEmpty();
        }

        TenantOrg tenant = getTenantById(id, true);
        if (tenant.getNamespace() != null && !tenant.getNamespace().isEmpty()) {
            throw APIException.badRequests.TenantNamespaceMappingConflict(id.toString(), tenant.getNamespace());
        }

        tenant.setNamespace(namespace);
        _dbClient.persistObject(tenant);

        auditOp(OperationTypeEnum.SET_TENANT_NAMESPACE, true, null, id.toString(), tenant.getLabel(), namespace);
        return map(getTenantById(id, false));
    }

    /**
     * Get namespace attached with a tenant or subtenant
     * @param id the URN of a ViPR Tenant/Subtenant
     * @return the TenantNamespaceInfo 
     */
    @GET
    @Path("/{id}/namespace")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
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
        String origNamespace = (tenant.getNamespace() == null) ? "": tenant.getNamespace();

        tenant.setNamespace("");
        _dbClient.persistObject(tenant);

        auditOp(OperationTypeEnum.UNSET_TENANT_NAMESPACE, true, null, id.toString(), tenant.getLabel(), origNamespace);
        return Response.ok().build();
    }
}


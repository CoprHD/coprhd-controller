/*
 * Copyright 2015-2016 Dell Inc. or its subsidiaries.
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
package com.emc.sa.api;

import static com.emc.sa.api.mapper.CatalogServiceMapper.createNewObject;
import static com.emc.sa.api.mapper.CatalogServiceMapper.createNewObjectList;
import static com.emc.sa.api.mapper.CatalogServiceMapper.map;
import static com.emc.sa.api.mapper.CatalogServiceMapper.toCatalogServiceList;
import static com.emc.sa.api.mapper.CatalogServiceMapper.updateObject;
import static com.emc.sa.api.mapper.CatalogServiceMapper.updateObjectList;
import static com.emc.storageos.db.client.URIUtil.uri;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.api.mapper.CatalogServiceFilter;
import com.emc.sa.api.utils.CatalogACLInputFilter;
import com.emc.sa.api.utils.CatalogConfigUtils;
import com.emc.sa.api.utils.ValidationUtils;
import com.emc.sa.catalog.CatalogCategoryManager;
import com.emc.sa.catalog.CatalogServiceManager;
import com.emc.sa.catalog.ServiceDescriptorUtil;
import com.emc.sa.catalog.WorkflowServiceDescriptor;
import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceDescriptors;
import com.emc.sa.descriptor.ServiceField;
import com.emc.sa.model.util.SortedIndexUtils;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.uimodels.CatalogCategory;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.CatalogServiceAndFields;
import com.emc.storageos.db.client.model.uimodels.CatalogServiceField;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.vipr.model.catalog.CatalogServiceBulkRep;
import com.emc.vipr.model.catalog.CatalogServiceCommonParam;
import com.emc.vipr.model.catalog.CatalogServiceCreateParam;
import com.emc.vipr.model.catalog.CatalogServiceFieldParam;
import com.emc.vipr.model.catalog.CatalogServiceList;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.CatalogServiceUpdateParam;

@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN }, writeRoles = {
        Role.TENANT_ADMIN }, readAcls = { ACL.ANY })
@Path("/catalog/services")
public class CatalogServiceService extends CatalogTaggedResourceService {

    private static final String EVENT_SERVICE_TYPE = "catalog-service";

    @Autowired
    private CatalogServiceManager catalogServiceManager;

    @Autowired
    private CatalogCategoryManager catalogCategoryManager;

    @Autowired
    private RecordableEventManager eventManager;

    @Autowired
    private ServiceDescriptors serviceDescriptors;

    @Autowired
    private WorkflowServiceDescriptor workflowServiceDescriptor;

    private CatalogConfigUtils catalogConfigUtils;

    public void setCatalogConfigUtils(CatalogConfigUtils catalogConfigUtils) {
        this.catalogConfigUtils = catalogConfigUtils;
    }

    @Override
    protected CatalogService queryResource(URI id) {
        return getCatalogServiceById(id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        CatalogService catalogService = queryResource(id);
        CatalogCategory parentCatalogCategory = catalogCategoryManager.getCatalogCategoryById(catalogService.getCatalogCategoryId()
                .getURI());
        return uri(parentCatalogCategory.getTenant());
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.CATALOG_SERVICE;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Gets the map of service descriptors by service ID->descriptor.
     * 
     * @return the service descriptor mapping.
     */
    private Map<String, ServiceDescriptor> getServiceDescriptors() {
        Map<String, ServiceDescriptor> descriptors = new HashMap<>();
        for (ServiceDescriptor descriptor : serviceDescriptors.listDescriptors(Locale.getDefault())) {
            descriptors.put(descriptor.getServiceId(), descriptor);
        }
        return descriptors;
    }

    /**
     * Gets a service descriptor by ID, returning null if no such service exists.
     * 
     * @param serviceId
     *            the service ID.
     * @return the service descriptor, or null.
     */
    private ServiceDescriptor getServiceDescriptor(String serviceId) {
        try {
            return ServiceDescriptorUtil.getServiceDescriptorByName(serviceDescriptors, workflowServiceDescriptor, serviceId);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /**
     * Gets the service descriptor for the given service.
     * 
     * @param service
     *            the catalog service.
     * @return the service descriptor, or null.
     */
    private ServiceDescriptor getServiceDescriptor(CatalogService service) {
        return getServiceDescriptor(service.getBaseService());
    }

    /**
     * Get info for catalog category
     * 
     * @param id the URN of a Catalog Category
     * @prereq none
     * @brief Show catalog category
     * @return Catalog Category details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public CatalogServiceRestRep getCatalogService(@PathParam("id") URI id) {
        CatalogService catalogService = queryResource(id);
        ServiceDescriptor serviceDescriptor = getServiceDescriptor(catalogService);
        List<CatalogServiceField> catalogServiceFields = catalogServiceManager.getCatalogServiceFields(catalogService.getId());
        return map(catalogService, serviceDescriptor, catalogServiceFields);
    }

    /**
     * Creates a new catalog service
     * 
     * @param createParam
     *            the parameter to create a new catalog service
     * @prereq none
     * @brief Create Catalog Service
     * @return none
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    @Path("")
    public CatalogServiceRestRep createCatalogService(CatalogServiceCreateParam createParam) {

        StorageOSUser user = getUserFromContext();
        CatalogCategory parentCatalogCategory = catalogCategoryManager.getCatalogCategoryById(createParam.getCatalogCategory());
        verifyAuthorizedInTenantOrg(uri(parentCatalogCategory.getTenant()), user);

        validateParam(createParam, null);

        CatalogService catalogService = createNewObject(createParam, parentCatalogCategory);
        List<CatalogServiceField> catalogServiceFields = createNewObjectList(catalogService, createParam.getCatalogServiceFields());
        catalogServiceManager.createCatalogService(catalogService, catalogServiceFields);

        auditOpSuccess(OperationTypeEnum.CREATE_CATALOG_SERVICE, catalogService.auditParameters());

        // Refresh Objects
        catalogService = catalogServiceManager.getCatalogServiceById(catalogService.getId());
        catalogServiceFields = catalogServiceManager.getCatalogServiceFields(catalogService.getId());
        ServiceDescriptor serviceDescriptor = getServiceDescriptor(catalogService);
        return map(catalogService, serviceDescriptor, catalogServiceFields);
    }

    /**
     * Update catalog service
     * 
     * @param param Catalog Service update parameters
     * @param id the URN the catalog service
     * @prereq none
     * @brief Update Catalog Service
     * @return No data returned in response body
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    public CatalogServiceRestRep updateCatalogService(@PathParam("id") URI id, CatalogServiceUpdateParam param) {
        CatalogService catalogService = getCatalogServiceById(id, true);
        List<CatalogServiceField> catalogServiceFields = catalogServiceManager.getCatalogServiceFields(id);

        StorageOSUser user = getUserFromContext();
        CatalogCategory parentCatalogCategory = catalogCategoryManager.getCatalogCategoryById(param.getCatalogCategory());
        verifyAuthorizedInTenantOrg(uri(parentCatalogCategory.getTenant()), user);

        validateParam(param, catalogService);

        updateObject(catalogService, param, parentCatalogCategory);
        List<CatalogServiceField> updatedCatalogServiceFields = updateObjectList(catalogService, catalogServiceFields,
                param.getCatalogServiceFields());

        catalogServiceManager.updateCatalogService(catalogService, updatedCatalogServiceFields);
        auditOpSuccess(OperationTypeEnum.UPDATE_CATALOG_SERVICE, catalogService.auditParameters());

        // Refresh Objects
        catalogService = catalogServiceManager.getCatalogServiceById(catalogService.getId());
        catalogServiceFields = catalogServiceManager.getCatalogServiceFields(catalogService.getId());
        ServiceDescriptor serviceDescriptor = getServiceDescriptor(catalogService);

        return map(catalogService, serviceDescriptor, catalogServiceFields);
    }

    @GET
    @Path("/{id}/acl")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.TENANT_ADMIN }, acls = { ACL.OWN })
    public ACLAssignments getRoleAssignments(@PathParam("id") URI id) {
        return getRoleAssignmentsResponse(id);
    }

    @PUT
    @Path("/{id}/acl")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.TENANT_ADMIN }, acls = { ACL.OWN }, blockProxies = true)
    public ACLAssignments updateRoleAssignments(@PathParam("id") URI id,
            ACLAssignmentChanges changes) {
        CatalogService catalogService = catalogServiceManager.getCatalogServiceById(id);
        CatalogCategory parentCatalogCategory = catalogCategoryManager.getCatalogCategoryById(catalogService.getCatalogCategoryId()
                .getURI());
        URI tenantId = uri(parentCatalogCategory.getTenant());
        
        TenantOrg tenantOrg = _permissionsHelper.getObjectById(tenantId, TenantOrg.class);
        _permissionsHelper.updateACLs(catalogService, changes, new CatalogACLInputFilter(tenantOrg));

        catalogServiceManager.updateCatalogService(catalogService, null);

        auditOpSuccess(OperationTypeEnum.MODIFY_CATALOG_SERVICE_ACL, catalogService.getId()
                .toString(), catalogService.getLabel(), changes);

        catalogConfigUtils.notifyCatalogAclChange();

        return getRoleAssignmentsResponse(id);
    }

    @PUT
    @Path("/{id}/move/up")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    public Response moveUpCatalogService(@PathParam("id") URI id) {

        catalogServiceManager.moveUpCatalogService(id);

        return Response.ok().build();
    }

    @PUT
    @Path("/{id}/move/down")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    public Response moveDownCatalogService(@PathParam("id") URI id) {

        catalogServiceManager.moveDownCatalogService(id);

        return Response.ok().build();
    }

    private ACLAssignments getRoleAssignmentsResponse(URI id) {
        CatalogService catalogService = catalogServiceManager.getCatalogServiceById(id);
        ACLAssignments response = new ACLAssignments();
        response.setAssignments(_permissionsHelper.convertToACLEntries(catalogService.getAcls()));
        return response;
    }

    @PUT
    @Path("/{id}/fields/{name}/move/up")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    public Response moveUpCatalogServiceField(@PathParam("id") URI id, @PathParam("name") String name) {

        catalogServiceManager.moveUpCatalogServiceField(id, name);

        return Response.ok().build();
    }

    @PUT
    @Path("/{id}/fields/{name}/move/down")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    public Response moveDownCatalogService(@PathParam("id") URI id, @PathParam("name") String name) {

        catalogServiceManager.moveDownCatalogServiceField(id, name);

        return Response.ok().build();
    }

    /**
     * Deactivates the catalog service
     * 
     * @param id the URN of an catalog service to be deactivated
     * @brief Deactivate Catalog Service
     * @return OK if deactivation completed successfully
     * @throws DatabaseException when a DB error occurs
     */
    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public Response deactivateCatalogService(@PathParam("id") URI id) throws DatabaseException {
        CatalogService catalogService = queryResource(id);
        ArgValidator.checkEntity(catalogService, id, true);

        catalogServiceManager.deleteCatalogService(catalogService);

        auditOpSuccess(OperationTypeEnum.DELETE_CATALOG_SERVICE, catalogService.auditParameters());

        return Response.ok().build();
    }

    /**
     * Gets the list a user's recent services
     * 
     * @brief List user's recent catalog services
     * @return a list of user's recent services
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("/recent")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CatalogServiceList getRecentCatalogServices() throws DatabaseException {

        StorageOSUser user = getUserFromContext();

        List<CatalogService> catalogServices = catalogServiceManager.getRecentCatalogServices(user);

        return toCatalogServiceList(catalogServices);
    }

    private CatalogService getCatalogServiceById(URI id, boolean checkInactive) {
        CatalogService catalogService = catalogServiceManager.getCatalogServiceById(id);
        ArgValidator.checkEntity(catalogService, id, isIdEmbeddedInURL(id), checkInactive);
        return catalogService;
    }

    private void validateParam(CatalogServiceCommonParam input, CatalogService existing) {

        ServiceDescriptor descriptor = getServiceDescriptor(input.getBaseService());

        if (descriptor == null) {
            throw APIException.badRequests.baseServiceNotFound(input.getBaseService());
        }

        if (null != input.getWorkflowName() && !input.getWorkflowName().isEmpty()) {
            if (null == catalogServiceManager.getWorkflowDocument(input.getWorkflowName())) {
                throw APIException.badRequests.workflowNotFound(input.getWorkflowName());
            }
        }

        for (CatalogServiceFieldParam field : input.getCatalogServiceFields()) {
            if (!field.getOverride()) {
                continue;
            }
            String fieldName = field.getName();
            String fieldValue = field.getValue();

            ServiceField descriptorField = descriptor.getField(fieldName);
            if (descriptorField != null) {
                ValidationUtils.validateField(input.getMaxSize(), descriptorField, fieldValue);
            }
        }

    }

    /**
     * List data for the specified services.
     * 
     * @param param POST data containing the id list.
     * @prereq none
     * @brief List data of specified services
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public CatalogServiceBulkRep getBulkResources(BulkIdParam param) {
        return (CatalogServiceBulkRep) super.getBulkResources(param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<CatalogService> getResourceClass() {
        return CatalogService.class;
    }

    @Override
    public CatalogServiceBulkRep queryBulkResourceReps(List<URI> ids) {
        return queryBulkResourceReps(ids, null);
    }

    @Override
    public CatalogServiceBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        CatalogServiceFilter filter = new CatalogServiceFilter(getUserFromContext(), _permissionsHelper, this.catalogCategoryManager);
        return queryBulkResourceReps(ids, filter);
    }

    private CatalogServiceBulkRep queryBulkResourceReps(List<URI> ids, CatalogServiceFilter filter) {
        List<CatalogServiceRestRep> catalogServiceRestReps = new ArrayList<CatalogServiceRestRep>();
        List<CatalogServiceAndFields> catalogServicesWithFields = catalogServiceManager
                .getCatalogServicesWithFields(ids);
        Map<String, ServiceDescriptor> descriptors = getServiceDescriptors();
        for (CatalogServiceAndFields catalogServiceAndField : catalogServicesWithFields) {
            if ((filter == null) || filter.isAccessible(catalogServiceAndField.getCatalogService())) {
                CatalogService service = catalogServiceAndField.getCatalogService();
                ServiceDescriptor descriptor = descriptors.get(service.getBaseService());
                List<CatalogServiceField> serviceFields = catalogServiceAndField.getCatalogServiceFields();
                catalogServiceRestReps.add(map(service, descriptor, serviceFields));
            }
        }

        catalogServiceRestReps = SortedIndexUtils.createSortedList(catalogServiceRestReps.iterator());
        return new CatalogServiceBulkRep(catalogServiceRestReps);
    }
}

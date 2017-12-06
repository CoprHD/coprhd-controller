/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api;

import static com.emc.sa.api.mapper.CatalogCategoryMapper.createNewCatalogCategory;
import static com.emc.sa.api.mapper.CatalogCategoryMapper.map;
import static com.emc.sa.api.mapper.CatalogCategoryMapper.updateCatalogCategoryObject;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.db.client.URIUtil.uri;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.api.mapper.CatalogCategoryFilter;
import com.emc.sa.api.mapper.CatalogCategoryMapper;
import com.emc.sa.api.mapper.CatalogServiceMapper;
import com.emc.sa.api.utils.CatalogACLInputFilter;
import com.emc.sa.api.utils.CatalogConfigUtils;
import com.emc.sa.catalog.CatalogCategoryManager;
import com.emc.sa.catalog.CatalogServiceManager;
import com.emc.sa.descriptor.ServiceDescriptors;
import com.emc.sa.model.util.SortedIndexUtils;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.uimodels.CatalogCategory;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.vipr.client.catalog.impl.SearchConstants;
import com.emc.vipr.model.catalog.CatalogCategoryBulkRep;
import com.emc.vipr.model.catalog.CatalogCategoryCommonParam;
import com.emc.vipr.model.catalog.CatalogCategoryCreateParam;
import com.emc.vipr.model.catalog.CatalogCategoryList;
import com.emc.vipr.model.catalog.CatalogCategoryRestRep;
import com.emc.vipr.model.catalog.CatalogCategoryUpdateParam;
import com.emc.vipr.model.catalog.CatalogServiceList;
import com.emc.vipr.model.catalog.CatalogUpgrade;
import com.google.common.collect.Lists;

@DefaultPermissions(
        readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN },
        writeRoles = { Role.TENANT_ADMIN },
        readAcls = { ACL.ANY })
@Path("/catalog/categories")
public class CatalogCategoryService extends CatalogTaggedResourceService {

    private static final Logger log = LoggerFactory.getLogger(CatalogCategoryService.class);

    private static final String EVENT_SERVICE_TYPE = "catalog-category";

    @Autowired
    private CatalogCategoryManager catalogCategoryManager;

    @Autowired
    private CatalogServiceManager catalogServiceManager;

    @Autowired
    private ServiceDescriptors serviceDescriptors;

    @Autowired
    private RecordableEventManager eventManager;

    private CatalogConfigUtils catalogConfigUtils;

    public void setCatalogConfigUtils(CatalogConfigUtils catalogConfigUtils) {
        this.catalogConfigUtils = catalogConfigUtils;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Override
    protected CatalogCategory queryResource(URI id) {
        return getCatalogCategoryById(id, false);
    }

    private CatalogCategory getCatalogCategoryById(URI id, boolean checkInactive) {
        CatalogCategory catalogCategory = catalogCategoryManager.getCatalogCategoryById(id);
        ArgValidator.checkEntity(catalogCategory, id, isIdEmbeddedInURL(id), checkInactive);
        return catalogCategory;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        CatalogCategory catalogCategory = queryResource(id);
        return uri(catalogCategory.getTenant());
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.CATALOG_CATEGORY;
    }

    /**
     * Get the root catalog category for a tenant
     * 
     * @prereq none
     * @brief Get Root Catalog Category
     * @return Root Catalog Category
     */
    @GET
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("")
    public CatalogCategoryRestRep getRootCatalogCategory(@DefaultValue("") @QueryParam(SearchConstants.TENANT_ID_PARAM) String tenantId) {

        StorageOSUser user = getUserFromContext();
        if (StringUtils.isBlank(tenantId)) {
            tenantId = user.getTenantId();
        }

        verifyAuthorizedInTenantOrg(uri(tenantId), user);

        CatalogCategory catalogCategory = catalogCategoryManager.getOrCreateRootCategory(uri(tenantId));

        return map(catalogCategory);
    }

    /**
     * List data for the specified categories.
     * 
     * @param param POST data containing the id list.
     * @prereq none
     * @brief List data of specified clusters
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public CatalogCategoryBulkRep getBulkResources(BulkIdParam param) {
        return (CatalogCategoryBulkRep) super.getBulkResources(param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<CatalogCategory> getResourceClass() {
        return CatalogCategory.class;
    }

    @Override
    public CatalogCategoryBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<CatalogCategory> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        List<CatalogCategoryRestRep> bulkList = BulkList.wrapping(_dbIterator, CatalogCategoryMapper.getInstance());
        List<CatalogCategoryRestRep> catalogCategoryRestReps = SortedIndexUtils.createSortedList(bulkList.iterator());
        return new CatalogCategoryBulkRep(catalogCategoryRestReps);
    }

    @Override
    public CatalogCategoryBulkRep queryFilteredBulkResourceReps(List<URI> ids) {

        Iterator<CatalogCategory> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter filter = new CatalogCategoryFilter(getUserFromContext(), _permissionsHelper);
        List<CatalogCategoryRestRep> bulkList = BulkList.wrapping(_dbIterator, CatalogCategoryMapper.getInstance(), filter);
        List<CatalogCategoryRestRep> catalogCategoryRestReps = SortedIndexUtils.createSortedList(bulkList.iterator());
        return new CatalogCategoryBulkRep(catalogCategoryRestReps);
    }

    /**
     * Get the root catalog category for a tenant
     * 
     * @prereq none
     * @brief Get Root Catalog Category
     * @return Root Catalog Category
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/reset")
    public Response resetRootCatalogCategory(@DefaultValue("") @QueryParam(SearchConstants.TENANT_ID_PARAM) String tenantId) {

        StorageOSUser user = getUserFromContext();
        if (StringUtils.isBlank(tenantId)) {
            tenantId = user.getTenantId();
        }

        verifyAuthorizedInTenantOrg(uri(tenantId), user);
        try {
            catalogCategoryManager.restoreDefaultCatalog(uri(tenantId));
        } catch (IOException e) {
            log.error("Failed to reset catalog", e);
            return Response.serverError().build();
        }

        return Response.ok().build();
    }

    /**
     * Check to see if an update to the service catalog is available
     * 
     * @prereq none
     * @brief Check for Service Catalog Update
     * @return True if Update Available
     */
    @GET
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/upgrade")
    public CatalogUpgrade upgradeAvailable(@DefaultValue("") @QueryParam(SearchConstants.TENANT_ID_PARAM) String tenantId) {

        StorageOSUser user = getUserFromContext();
        if (StringUtils.isBlank(tenantId)) {
            tenantId = user.getTenantId();
        }

        verifyAuthorizedInTenantOrg(uri(tenantId), user);

        CatalogUpgrade catalogUpgrade = new CatalogUpgrade();
        catalogUpgrade.setUpgradeAvailable(catalogCategoryManager.isCatalogUpdateAvailable(uri(tenantId)));
        return catalogUpgrade;
    }

    /**
     * Update the service catalog to the latest version
     * 
     * @prereq none
     * @brief Service Catalog Update
     * @return none
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/upgrade")
    public Response upgradeCatalog(@DefaultValue("") @QueryParam(SearchConstants.TENANT_ID_PARAM) String tenantId) {

        StorageOSUser user = getUserFromContext();
        if (StringUtils.isBlank(tenantId)) {
            tenantId = user.getTenantId();
        }

        verifyAuthorizedInTenantOrg(uri(tenantId), user);
        try {
            catalogCategoryManager.upgradeCatalog(uri(tenantId));
        } catch (IOException e) {
            log.error("Failed to upgrade catalog", e);
            return Response.serverError().build();
        }

        return Response.ok().build();
    }

    /**
     * Creates a new catalog category in the supplied parent catalog category
     * 
     * @param createParam
     *            the parameter to create a new catalog category
     * @prereq none
     * @brief Create Catalog Category
     * @return none
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("")
    public CatalogCategoryRestRep createCatalogCategory(CatalogCategoryCreateParam createParam) {

        StorageOSUser user = getUserFromContext();
        String tenantId = createParam.getTenantId();

        if (StringUtils.isBlank(tenantId)) {
            tenantId = user.getTenantId();
        }

        verifyAuthorizedInTenantOrg(uri(tenantId), user);

        CatalogCategory parentCatalogCategory = queryResource(createParam.getCatalogCategoryId());
        validateCatalogCategoryParam(createParam.getCatalogCategoryId(), createParam, null);

        CatalogCategory catalogCategory = createNewCatalogCategory(parentCatalogCategory, createParam);

        catalogCategoryManager.createCatalogCategory(catalogCategory);

        auditOpSuccess(OperationTypeEnum.CREATE_CATALOG_CATEGORY, catalogCategory.auditParameters());

        return map(catalogCategory);
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
    public CatalogCategoryRestRep getCatalogCategory(@PathParam("id") URI id) {
        CatalogCategory catalogCategory = queryResource(id);
        return map(catalogCategory);
    }

    @PUT
    @Path("/{id}/move/up")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    public Response moveUpCatalogCategory(@PathParam("id") URI id) {

        catalogCategoryManager.moveUpCatalogCategory(id);

        return Response.ok().build();
    }

    @PUT
    @Path("/{id}/move/down")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    public Response moveDownCatalogCategory(@PathParam("id") URI id) {

        catalogCategoryManager.moveDownCatalogCategory(id);

        return Response.ok().build();
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
        CatalogCategory catalogCategory = catalogCategoryManager.getCatalogCategoryById(id);
        URI tenantId = uri(catalogCategory.getTenant());

        TenantOrg tenantOrg = _permissionsHelper.getObjectById(tenantId, TenantOrg.class);

        _permissionsHelper.updateACLs(catalogCategory, changes, new CatalogACLInputFilter(tenantOrg));

        catalogCategoryManager.updateCatalogCategory(catalogCategory);
        ;

        auditOpSuccess(OperationTypeEnum.MODIFY_CATALOG_CATEGORY_ACL, catalogCategory.getId()
                .toString(), catalogCategory.getLabel(), changes);

        catalogConfigUtils.notifyCatalogAclChange();

        return getRoleAssignmentsResponse(id);
    }

    private ACLAssignments getRoleAssignmentsResponse(URI id) {
        CatalogCategory catalogCategory = catalogCategoryManager.getCatalogCategoryById(id);
        ACLAssignments response = new ACLAssignments();
        response.setAssignments(_permissionsHelper.convertToACLEntries(catalogCategory.getAcls()));
        return response;
    }

    /**
     * Gets the list of sub categories within the category
     * 
     * @param id the URN of a Catalog Category
     * @brief List Catalog Categories
     * @return a list of sub categories that belong to the category
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("/{id}/categories")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CatalogCategoryList getCatalogCategories(@PathParam("id") URI id) throws DatabaseException {
        CatalogCategory parentCatalogCategory = queryResource(id);

        // check the user permissions
        verifyAuthorizedInTenantOrg(uri(parentCatalogCategory.getTenant()), getUserFromContext());

        List<CatalogCategory> subCatalogCategories = catalogCategoryManager.getSubCategories(id);
        subCatalogCategories = filterCategoriesByACLs(subCatalogCategories);

        CatalogCategoryList subCatalogCategoryList = new CatalogCategoryList();
        for (CatalogCategory subCatalogCategory : subCatalogCategories) {
            NamedRelatedResourceRep subCatalogCategoryRestRep = toNamedRelatedResource(ResourceTypeEnum.CATALOG_CATEGORY,
                    subCatalogCategory.getId(), subCatalogCategory.getLabel());
            subCatalogCategoryList.getCatalogCategories().add(subCatalogCategoryRestRep);
        }

        return subCatalogCategoryList;
    }

    /**
     * Update info for catalog category
     * 
     * @param catalogCategoryUpdate Catalog Category update parameters
     * @param id the URN of a Catalog Category
     * @prereq none
     * @brief Update Catalog Category
     * @return No data returned in response body
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    public CatalogCategoryRestRep updateCatalogCategory(@PathParam("id") URI id, CatalogCategoryUpdateParam catalogCategoryUpdate) {
        CatalogCategory catalogCategory = getCatalogCategoryById(id, true);

        StorageOSUser user = getUserFromContext();
        verifyAuthorizedInTenantOrg(uri(catalogCategory.getTenant()), user);

        URI parentCategoryId = getTargetParentCategoryId(catalogCategory, catalogCategoryUpdate);
        validateCatalogCategoryParam(parentCategoryId, catalogCategoryUpdate, catalogCategory);
        CatalogCategory parentCatalogCategory = parentCategoryId != null ? queryResource(parentCategoryId) : null;
        updateCatalogCategoryObject(parentCatalogCategory, catalogCategory, catalogCategoryUpdate);

        catalogCategoryManager.updateCatalogCategory(catalogCategory);

        auditOpSuccess(OperationTypeEnum.UPDATE_CATALOG_CATEGORY, catalogCategory.auditParameters());

        catalogCategory = catalogCategoryManager.getCatalogCategoryById(catalogCategory.getId());

        return map(catalogCategory);
    }

    /**
     * Gets the target parent category ID for a category update.
     * 
     * @param category the category to update.
     * @param update the update parameters.
     * @return the target parent ID.
     */
    private URI getTargetParentCategoryId(CatalogCategory category, CatalogCategoryUpdateParam update) {
        if (update.getCatalogCategoryId() != null) {
            return NullColumnValueGetter.normalize(update.getCatalogCategoryId());
        }
        if (CatalogCategory.isRoot(category)) {
            return null;
        }
        NamedURI parentId = NullColumnValueGetter.normalize(category.getCatalogCategoryId());
        return parentId != null ? parentId.getURI() : null;
    }

    /**
     * Deactivates the catalog category
     * 
     * @param id the URN of a catalog category to be deactivated
     * @brief Deactivate Catalog Category
     * @return OK if deactivation completed successfully
     * @throws DatabaseException when a DB error occurs
     */
    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public Response deactivateCatalogCategory(@PathParam("id") URI id) throws DatabaseException {
        CatalogCategory catalogCategory = queryResource(id);
        ArgValidator.checkEntity(catalogCategory, id, true);

        catalogCategoryManager.deleteCatalogCategory(catalogCategory);

        auditOpSuccess(OperationTypeEnum.DELETE_CATALOG_CATEGORY, catalogCategory.auditParameters());

        return Response.ok().build();
    }

    /**
     * Gets the list of catalog services
     * 
     * @param catalogCategoryId the URN of a catalog category
     * @brief List Catalog Services
     * @return a list of catalog services
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("/{id}/services")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CatalogServiceList getCatalogServices(@PathParam("id") String catalogCategoryId) throws DatabaseException {

        List<CatalogService> catalogServices = catalogServiceManager.getCatalogServices(uri(catalogCategoryId));
        catalogServices = filterServicesByACLs(catalogServices);

        return CatalogServiceMapper.toCatalogServiceList(catalogServices);
    }

    private void validateCatalogCategoryParam(URI parentCatalogCategoryId, CatalogCategoryCommonParam input,
            CatalogCategory existingCatalogCategory) {
        if (nameChanged(input, existingCatalogCategory)) {
            checkForDuplicateName(input.getName(), CatalogCategory.class, parentCatalogCategoryId, CatalogCategory.CATALOG_CATEGORY_ID,
                    _dbClient);
        }
    }

    private boolean nameChanged(CatalogCategoryCommonParam catalogCategoryCommonParam, CatalogCategory existingCatalogCategory) {
        return existingCatalogCategory == null
                || (StringUtils.isNotBlank(catalogCategoryCommonParam.getName()) && !StringUtils.equals(
                        catalogCategoryCommonParam.getName(), existingCatalogCategory.getLabel()));
    }

    /**
     * filter out the services which user don't have access to
     * 
     * @param services
     * @return
     */
    private List<CatalogService> filterServicesByACLs(List<CatalogService> services) {
        List<CatalogService> filteredCatalogServices = Lists.newArrayList();

        StorageOSUser storageOSUser = getUserFromContext();
        String username = storageOSUser.getName();
        if (isAdministrator(storageOSUser)) {
            log.debug(username + " has SystemAdmin or TenantAdmin Role, can view all categories.");
            filteredCatalogServices.addAll(services);
            return filteredCatalogServices;
        }

        for (CatalogService service : services) {
            if (hasAccess(storageOSUser, service)) {
                filteredCatalogServices.add(service);
            }
        }
        return filteredCatalogServices;
    }

    /**
     * check if user has access to the service
     * 
     * @param storageOSUser
     * @param service
     * @return
     */
    private boolean hasAccess(StorageOSUser storageOSUser, CatalogService service) {
        log.debug("check if " + storageOSUser.getName() + " has access for " + service.getTitle());
        return hasAccess(storageOSUser, service.getAcls());
    }

    /**
     * check if user has access to the category
     * 
     * @param storageOSUser
     * @param category
     * @return
     */
    private boolean hasAccess(StorageOSUser storageOSUser, CatalogCategory category) {
        log.debug("check if " + storageOSUser.getName() + " has access for " + category.getTitle());
        return hasAccess(storageOSUser, category.getAcls());
    }

    /**
     * check if specified acls permission user to access
     * 
     * @param storageOSUser
     * @param acls
     * @return
     */
    private boolean hasAccess(StorageOSUser storageOSUser, StringSetMap acls) {
        // no acl set
        if (acls == null || acls.isEmpty()) {
            log.debug("acls is empty, pass");
            return true;
        }

        // acl is not empty, check if the user is allowed.
        List<ACLEntry> aclEntries = _permissionsHelper.convertToACLEntries(acls);
        String username = storageOSUser.getName();
        for (ACLEntry entry : aclEntries) {
            if (entry.getSubjectId() != null && entry.getSubjectId().equalsIgnoreCase(username)) {
                log.debug("has acls contain subjectId for current user: " + username);
                return true;
            } else if (entry.getGroup() != null) {
                for (String group : storageOSUser.getGroups()) {
                    if (group.equalsIgnoreCase(entry.getGroup())) {
                        log.debug("has acls contain group for current user: " + entry.getGroup());
                        return true;
                    }
                }
            } else {
                continue;
            }
        }

        // acl is set, but user
        log.debug("has acls, but current user is not in them: " + username);
        return false;
    }

    /**
     * filter out the categories which user don't have access to
     * 
     * @param categories
     * @return
     */
    private List<CatalogCategory> filterCategoriesByACLs(List<CatalogCategory> categories) {
        List<CatalogCategory> filteredCatalogCategories = Lists.newArrayList();

        StorageOSUser storageOSUser = getUserFromContext();
        String username = storageOSUser.getName();
        if (isAdministrator(storageOSUser)) {
            log.debug(username + " has SystemAdmin or TenantAdmin Role, can view all categories.");
            filteredCatalogCategories.addAll(categories);
            return filteredCatalogCategories;
        }

        for (CatalogCategory category : categories) {
            if (hasAccess(storageOSUser, category)) {
                filteredCatalogCategories.add(category);
            }
        }
        return filteredCatalogCategories;
    }

    /**
     * check if user has System admin or TenantAdmin role
     * 
     * @param storageOSUser
     * @return
     */
    private boolean isAdministrator(StorageOSUser storageOSUser) {
        for (String role : storageOSUser.getRoles()) {
            if (role.equalsIgnoreCase(Role.SYSTEM_ADMIN.toString())) {
                return true;
            }
        }

        Set<String> tenantRoles = _permissionsHelper.getTenantRolesForUser(storageOSUser,
                URI.create(storageOSUser.getTenantId()), false);
        for (String role : tenantRoles) {
            if (role.equalsIgnoreCase(Role.TENANT_ADMIN.toString())) {
                return true;
            }
        }

        return false;
    }

}

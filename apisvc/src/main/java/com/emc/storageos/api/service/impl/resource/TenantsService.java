/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.toLink;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.HostMapper.map;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.mapper.functions.MapTenant;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPermissionsConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObjectWithACLs;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.ECSNamespace;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.TenantResource;
import com.emc.storageos.db.client.model.UserGroup;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.auth.PrincipalsToValidate;
import com.emc.storageos.model.auth.RoleAssignmentChanges;
import com.emc.storageos.model.auth.RoleAssignmentEntry;
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
import com.emc.storageos.model.tenant.TenantCreateParam;
import com.emc.storageos.model.tenant.TenantOrgBulkRep;
import com.emc.storageos.model.tenant.TenantOrgList;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.model.tenant.TenantUpdateParam;
import com.emc.storageos.model.tenant.UserMappingAttributeParam;
import com.emc.storageos.model.tenant.UserMappingParam;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMapping;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.PermissionsKey;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.resource.UserInfoPage;
import com.emc.storageos.security.validator.StorageOSPrincipal;
import com.emc.storageos.security.validator.Validator;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.svcs.errorhandling.resources.ForbiddenException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

/**
 * API for creating and manipulating tenants
 */

@Path("/tenants")
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.TENANT_ADMIN })
public class TenantsService extends TaggedResource {
    private static final String EVENT_SERVICE_TYPE = "tenant";
    private static final String EVENT_SERVICE_SOURCE = "TenantManager";
    private static final Logger _log = LoggerFactory.getLogger(TenantsService.class);

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    private HostService _hostService;

    @Autowired
    private VcenterService _vcenterService;

    @Autowired
    private ClusterService _clusterService;

    /**
     * Get info for tenant or subtenant
     * 
     * @param id the URN of a ViPR Tenant/Subtenant
     * @prereq none
     * @brief Show tenant or subtenant
     * @return Tenant details
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN, Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN})
    public TenantOrgRestRep getTenant(@PathParam("id") URI id) {
        return map(getTenantById(id, false));
    }

    @Override
    protected DataObject queryResource(URI id) {
        return getTenantById(id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return id;
    }

    /**
     * Filter class for validating input args for role assignments
     */
    private class TenantRoleInputFilter extends PermissionsHelper.RoleInputFilter {
        protected List<String> _tenantAdminUsers;

        public TenantRoleInputFilter(TenantOrg tenant) {
            super(tenant);
        }

        @Override
        public boolean isValidRole(String ace) {
            return _permissionsHelper.isRoleTenantLevel(ace);
        }

        @Override
        public StringSetMap convertFromRolesRemove(List<RoleAssignmentEntry> remove) {
            return convertFromRoleAssignments(remove);
        }

        @Override
        public void initLists() {
            super.initLists();
            _tenantAdminUsers = new ArrayList<String>();
        }

        @Override
        protected void addPrincipalToList(PermissionsKey key,
                RoleAssignmentEntry roleAssignment) {
            switch (key.getType()) {
                case GROUP:
                    _groups.add(key.getValue());
                    break;
                case SID:
                    if (roleAssignment.getRoles().contains(Role.TENANT_ADMIN.toString())) {
                        _tenantAdminUsers.add(key.getValue());
                    } else {
                        _users.add(key.getValue());
                    }
                    break;
                case TENANT:
                default:
                    break;
            }
        }

        @Override
        public void validatePrincipals() {
            StringBuilder error = new StringBuilder();

            // we allow exceptions if the role we want to assign is
            // TENANT_ADMIN, and either the user is root, or it belongs to the
            // root tenant
            _tenantAdminUsers.remove("root");

            PrincipalsToValidate principalsToValidate = new PrincipalsToValidate();
            principalsToValidate.setTenantId(_tenant.getId().toString());
            principalsToValidate.setGroups(_groups);
            principalsToValidate.setUsers(_users);
            principalsToValidate.setAltTenantId(_tenant.getParentTenant().getURI()
                    .toString());
            principalsToValidate.setAltTenantUsers(_tenantAdminUsers);

            if (!Validator.validatePrincipals(principalsToValidate, error)) {
                throw APIException.badRequests.invalidRoleAssignments(error.toString());
            }
        }

        @Override
        public StringSetMap convertFromRolesAdd(List<RoleAssignmentEntry> add,
                boolean validate) {
            StringSetMap returnedStringSetMap = convertFromRoleAssignments(add);

            if (!CollectionUtils.isEmpty(_rootRoleAssignments)
                    && !VdcUtil.isLocalVdcSingleSite()) {
                throw APIException.badRequests
                        .invalidRoleAssignments("Tenant Roles can't be assigned to root in GEO scenario.");

            }

            if (!CollectionUtils.isEmpty(_rootRoleAssignments)
                    && _rootRoleAssignments.size() == 1
                    && _rootRoleAssignments.get(0).equalsIgnoreCase(
                            Role.TENANT_ADMIN.toString())) {
                _localUsers.remove("root");
            }

            if (!CollectionUtils.isEmpty(_localUsers)) {
                throw APIException.badRequests
                        .invalidEntryForRoleAssignmentSubjectIdsCannotBeALocalUsers(StringUtils
                                .join(_localUsers, ", "));
            }

            if (validate) {
                validatePrincipals();
            }
            return returnedStringSetMap;
        }
    }

    /**
     * Update info for tenant or subtenant
     * 
     * @param param Tenant update parameter
     * @param id the URN of a ViPR Tenant/Subtenant
     * @prereq If modifying user mappings, an authentication provider needs to support the domain used in the mappings
     * @brief Update tenant or subtenant
     * @return the updated Tenant/Subtenant instance
     */
    @PUT
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN, Role.SECURITY_ADMIN })
    public TenantOrgRestRep setTenant(@PathParam("id") URI id, TenantUpdateParam param) {
        TenantOrg tenant = getTenantById(id, true);
        if (param.getLabel() != null && !param.getLabel().isEmpty()) {
            if (!tenant.getLabel().equalsIgnoreCase(param.getLabel())) {
                checkForDuplicateName(param.getLabel(), TenantOrg.class, tenant.getParentTenant()
                        .getURI(), "parentTenant", _dbClient);
            }
            tenant.setLabel(param.getLabel());
            NamedURI parent = tenant.getParentTenant();
            if (parent != null) {
                parent.setName(param.getLabel());
                tenant.setParentTenant(parent);
            }
        }

        if (param.getDescription() != null) {
            tenant.setDescription(param.getDescription());
        }
        
        if (param.getNamespace() != null && !param.getNamespace().isEmpty()) {
            checkForDuplicateNamespace(param.getNamespace());

            if (tenant.getNamespace() != null && !tenant.getNamespace().isEmpty()) {
                if (!tenant.getNamespace().equalsIgnoreCase(param.getNamespace())) {
                    //Though we are not deleting need to check no dependencies on this tenant
                    ArgValidator.checkReference(TenantOrg.class, id, checkForDelete(tenant));
                }
            }
            tenant.setNamespace(param.getNamespace());
            //Update tenant info in respective namespace CF
            List<URI> allNamespaceURI = _dbClient.queryByType(ECSNamespace.class, true);
            Iterator<ECSNamespace> nsItr = _dbClient.queryIterativeObjects(ECSNamespace.class, allNamespaceURI);
            while (nsItr.hasNext()) {
                ECSNamespace namesp = nsItr.next();
                if (namesp.getNativeId().equalsIgnoreCase(tenant.getNamespace()) &&
                        namesp.getStorageDevice().equals(param.getNamespaceStorage())) {
                    namesp.setTenant(tenant.getId());
                    namesp.setMapped(true);
                    _dbClient.updateObject(namesp);
                    break;
                }
            }
        }

        if (!isUserMappingEmpty(param)) {
            // only SecurityAdmin can modify user-mapping
            if (!_permissionsHelper.userHasGivenRole(
                    (StorageOSUser) sc.getUserPrincipal(),
                    null, Role.SECURITY_ADMIN)) {
                throw ForbiddenException.forbidden.onlySecurityAdminsCanModifyUserMapping();
            }

            if (null != param.getUserMappingChanges().getRemove() && !param.getUserMappingChanges().getRemove().isEmpty()
                    && null != tenant.getUserMappings()) {
                checkUserMappingAttribute(param.getUserMappingChanges().getRemove());
                List<UserMapping> remove = UserMapping.fromParamList(param.getUserMappingChanges().getRemove());
                StringSetMap mappingsToRemove = new StringSetMap();
                // Find the database entries to remove
                for (UserMapping mappingToRemove : remove) {
                    StringSet domainMappings = tenant.getUserMappings().get(mappingToRemove.getDomain().trim());
                    trimGroupAndDomainNames(mappingToRemove);
                    if (null != domainMappings) {
                        for (String existingMapping : domainMappings) {
                            if (mappingToRemove.equals(UserMapping.fromString(existingMapping))) {
                                mappingsToRemove.put(mappingToRemove.getDomain(), existingMapping);
                            }
                        }
                    }
                }

                // Remove the items from the tenant database object
                for (Entry<String, AbstractChangeTrackingSet<String>> mappingToRemoveSet : mappingsToRemove
                        .entrySet()) {
                    for (String mappingToRemove : mappingToRemoveSet.getValue()) {
                        tenant.removeUserMapping(mappingToRemoveSet.getKey(),
                                mappingToRemove);
                    }
                }
            }

            if (null != param.getUserMappingChanges().getAdd() && !param.getUserMappingChanges().getAdd().isEmpty()) {
                checkUserMappingAttribute(param.getUserMappingChanges().getAdd());
                addUserMappings(tenant, param.getUserMappingChanges().getAdd(), getUserFromContext());
            }

            if (!TenantOrg.isRootTenant(tenant)) {
                boolean bMappingsEmpty = true;
                for (AbstractChangeTrackingSet<String> mapping : tenant
                        .getUserMappings().values()) {
                    if (!mapping.isEmpty()) {
                        bMappingsEmpty = false;
                        break;
                    }
                }
                if (bMappingsEmpty) {
                    throw APIException.badRequests
                            .requiredParameterMissingOrEmpty("user_mappings");
                }
            }

            // request contains user-mapping change, perform the check.
            mapOutProviderTenantCheck(tenant);
        }

        _dbClient.updateAndReindexObject(tenant);

        recordOperation(OperationTypeEnum.UPDATE_TENANT, tenant.getId(), tenant);
        return map(getTenantById(id, false));
    }

    /**
     * Create subtenant
     * 
     * @param param Subtenant create parameter
     * @param id the URN of a ViPR Tenant
     * @prereq An authentication provider needs to support the domain used in the mappings
     * @brief Create subtenant
     * @return Subtenant details
     */
    @POST
    @Path("/{id}/subtenants")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public TenantOrgRestRep createSubTenant(@PathParam("id") URI id,
            TenantCreateParam param) {
        TenantOrg parent = getTenantById(id, true);
        if (!TenantOrg.isRootTenant(parent)) {
            throw APIException.badRequests.parentTenantIsNotRoot();
        }

        ArgValidator.checkFieldNotEmpty(param.getLabel(), "name");
        checkForDuplicateName(param.getLabel(), TenantOrg.class, id, "parentTenant", _dbClient);
        TenantOrg subtenant = new TenantOrg();
        subtenant.setId(URIUtil.createId(TenantOrg.class));
        subtenant.setParentTenant(new NamedURI(parent.getId(), param.getLabel()));
        subtenant.setLabel(param.getLabel());
        subtenant.setDescription(param.getDescription());
        if (param.getNamespace() != null) {
            checkForDuplicateNamespace(param.getNamespace());
            subtenant.setNamespace(param.getNamespace());
            //Update tenant info in respective namespace CF
            List<URI> allNamespaceURI = _dbClient.queryByType(ECSNamespace.class, true);
            Iterator<ECSNamespace> nsItr = _dbClient.queryIterativeObjects(ECSNamespace.class, allNamespaceURI);
            while (nsItr.hasNext()) {
                ECSNamespace namesp = nsItr.next();
                if (namesp.getNativeId().equalsIgnoreCase(subtenant.getNamespace()) &&
                        namesp.getStorageDevice().equals(param.getNamespaceStorage())) {
                    namesp.setTenant(subtenant.getId());
                    namesp.setMapped(true);
                    _dbClient.updateObject(namesp);
                    break;
                }
            }
        }

        if (null == param.getUserMappings() || param.getUserMappings().isEmpty()) {
            throw APIException.badRequests
                    .requiredParameterMissingOrEmpty("user_mappings");
        } else {
            checkUserMappingAttribute(param.getUserMappings());
            addUserMappings(subtenant, param.getUserMappings(), getUserFromContext());
        }
        // add creator as tenant admin
        subtenant.addRole(new PermissionsKey(PermissionsKey.Type.SID,
                getUserFromContext().getName()).toString(), Role.TENANT_ADMIN.toString());

        // perform user tenant check before persistent
        mapOutProviderTenantCheck(subtenant);

        _dbClient.createObject(subtenant);
        // To Do - add attributes to the set of attributes to pull from AD/LDAP

        recordOperation(OperationTypeEnum.CREATE_TENANT, parent.getId(), subtenant);
        return map(subtenant);
    }

    /**
     * List subtenants
     * 
     * @param id the URN of a ViPR Tenant
     * @prereq none
     * @brief List subtenants
     * @return List of subtenants
     */
    @GET
    @Path("/{id}/subtenants")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public TenantOrgList listSubTenants(@PathParam("id") URI id) {
        StorageOSUser user = getUserFromContext();
        TenantOrg tenant = getTenantById(id, false);
        TenantOrgList list = new TenantOrgList();
        if (!TenantOrg.isRootTenant(tenant)) {
            // no subtenants if not root tenant
            throw APIException.methodNotAllowed.notSupportedForSubtenants();
        }
        NamedElementQueryResultList subtenants = new NamedElementQueryResultList();
        if (_permissionsHelper.userHasGivenRole(user, tenant.getId(),
                Role.SYSTEM_MONITOR, Role.TENANT_ADMIN, Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN)) {
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getTenantOrgSubTenantConstraint(tenant.getId()), subtenants);
        } else {
            // we will most likely not need indexing for tenants
            // given the number of tenants is not going to be that many
            Set<String> roles = new HashSet<String>();
            roles.add(Role.TENANT_ADMIN.toString());
            Map<URI, Set<String>> allTenantPermissions =
                    _permissionsHelper.getAllPermissionsForUser(user, tenant.getId(),
                            roles, true);
            if (!allTenantPermissions.keySet().isEmpty()) {
                List<TenantOrg> tenants =
                        _dbClient.queryObjectField(TenantOrg.class, "label",
                                new ArrayList<URI>(allTenantPermissions.keySet()));
                List<NamedElementQueryResultList.NamedElement> elements =
                        new ArrayList<NamedElementQueryResultList.NamedElement>(
                                tenants.size());
                for (TenantOrg t : tenants) {
                    elements.add(NamedElementQueryResultList.NamedElement.createElement(
                            t.getId(), t.getLabel()));
                }
                subtenants.setResult(elements.iterator());
            } else {
                throw APIException.forbidden.insufficientPermissionsForUser(user
                        .getName());
            }
        }

        for (NamedElementQueryResultList.NamedElement el : subtenants) {
            list.getSubtenants().add(
                    toNamedRelatedResource(ResourceTypeEnum.TENANT, el.getId(), el.getName()));
        }
        return list;
    }

    /**
     * Deactivate the subtenant.
     * When a subtenant is deleted it will move to a "marked for deletion" state.
     * Once in this state, new projects may no longer be created in the subtenant.
     * The subtenant will be permanently deleted once all Projects and UserSecretKeys
     * referencing this tenant are deleted.
     * 
     * @param id the URN of a ViPR Tenant/Subtenant
     * @prereq none
     * @brief Delete subtenant
     * @return No data returned in response body
     */
    @POST
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public Response deactivateTenant(@PathParam("id") URI id) {
        TenantOrg tenant = getTenantById(id, true);
        if (TenantOrg.isRootTenant(tenant)) {
            // root can not be deleted
            throw APIException.badRequests.resourceCannotBeDeleted("Root tenant");
        }

        ArgValidator.checkReference(TenantOrg.class, id, checkForDelete(tenant));

        _dbClient.markForDeletion(tenant);

        recordOperation(OperationTypeEnum.DELETE_TENANT, tenant.getParentTenant()
                .getURI(), tenant);

        // clear references to this deleted sub-tenant from the USE ACL lists of vpools and varrays
        clearTenantACLs(VirtualPool.class, tenant.getId(), VirtualPool.Type.block.toString());
        clearTenantACLs(VirtualPool.class, tenant.getId(), VirtualPool.Type.file.toString());
        clearTenantACLs(VirtualArray.class, tenant.getId(), null);

        return Response.ok().build();
    }

    /**
     * Get tenant or subtenant role assignments
     * 
     * @param id the URN of a ViPR Tenant/Subtenant
     * @prereq none
     * @brief List tenant or subtenant role assignments
     * @return Role assignment details
     */
    @GET
    @Path("/{id}/role-assignments")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.TENANT_ADMIN })
    public RoleAssignments getRoleAssignments(@PathParam("id") URI id) {
        TenantOrg tenant = getTenantById(id, false);
        return getRoleAssignmentsResponse(tenant);
    }

    /**
     * Add or remove individual role assignments
     * 
     * @param changes Role Assignment changes
     * @param id the URN of a ViPR Tenant/Subtenant
     * @prereq none
     * @brief Add or remove role assignments
     * @return No data returned in response body
     */
    @PUT
    @Path("/{id}/role-assignments")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.TENANT_ADMIN },
            blockProxies = true)
    public RoleAssignments updateRoleAssignments(@PathParam("id") URI id,
            RoleAssignmentChanges changes) {
        TenantOrg tenant = getTenantById(id, true);
        _permissionsHelper.updateRoleAssignments(tenant, changes,
                new TenantRoleInputFilter(tenant));
        _dbClient.updateAndReindexObject(tenant);

        recordTenantEvent(OperationTypeEnum.MODIFY_TENANT_ROLES, tenant.getId(),
                tenant.getId());
        auditOp(OperationTypeEnum.MODIFY_TENANT_ROLES, true, null, tenant.getId()
                .toString(), tenant.getLabel(), changes);
        return getRoleAssignmentsResponse(tenant);
    }

    /**
     * Create a project
     * 
     * @param param Project parameters
     * @param id the URN of a ViPR Tenant/Subtenant
     * @prereq none
     * @brief Create project
     * @return Project details
     */
    @POST
    @Path("/{id}/projects")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN, Role.PROJECT_ADMIN })
    public ProjectElement createProject(@PathParam("id") URI id, ProjectParam param) {
        ProjectElement projectElement = createProject(id, param, getUserFromContext().getName(), getUserFromContext().getTenantId().toString());
        auditOp(OperationTypeEnum.CREATE_PROJECT, true, null, param.getName(),
                id.toString(), projectElement.getId().toString());
        return projectElement;        
    }

    /**
     * Worker method for create project.  Allows external requests (REST) as well as 
     * internal requests that may not have a security context.
     *   
     * @param id tenant id
     * @param param project params
     * @param owner name of owner of the request
     * @param ownerTenantId tenant id of the owner
     * @return project details
     */
    public ProjectElement createProject(URI id, ProjectParam param, String owner, String ownerTenantId) {
        TenantOrg tenant = getTenantById(id, true);
        if (param.getName() != null && !param.getName().isEmpty()) {
            checkForDuplicateName(param.getName(), Project.class, id, "tenantOrg", _dbClient);
        }
        
        Project project = new Project();
        project.setId(URIUtil.createId(Project.class));
        project.setLabel(param.getName());
        project.setTenantOrg(new NamedURI(tenant.getId(), project.getLabel()));
        project.setOwner(owner);

        // set owner acl
        project.addAcl(
                new PermissionsKey(PermissionsKey.Type.SID, owner, ownerTenantId).toString(), 
                ACL.OWN.toString());
        _dbClient.createObject(project);

        recordTenantEvent(OperationTypeEnum.CREATE_PROJECT, tenant.getId(),
                project.getId());

        return new ProjectElement(project.getId(), toLink(ResourceTypeEnum.PROJECT,
                project.getId()), project.getLabel());
    }

    /**
     * List projects the user is authorized to see
     * 
     * @param id the URN of a ViPR Tenant/Subtenant
     * @prereq none
     * @brief List projects
     * @return List of projects
     */
    @GET
    @Path("/{id}/projects")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ProjectList listProjects(@PathParam("id") URI id) {
        TenantOrg tenant = getTenantById(id, false);
        StorageOSUser user = getUserFromContext();
        NamedElementQueryResultList projects = new NamedElementQueryResultList();
        if (_permissionsHelper.userHasGivenRole(user, tenant.getId(),
                Role.SYSTEM_MONITOR, Role.TENANT_ADMIN, Role.SECURITY_ADMIN)) {
            // list all
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getTenantOrgProjectConstraint(tenant.getId()), projects);
        } else {
            // list only projects that the user has access to
            if (!id.equals(URI.create(user.getTenantId()))) {
                throw APIException.forbidden.insufficientPermissionsForUser(user
                        .getName());
            }
            Map<URI, Set<String>> allMyProjects =
                    _permissionsHelper.getAllPermissionsForUser(user, tenant.getId(),
                            null, false);
            if (!allMyProjects.keySet().isEmpty()) {
                List<Project> project_list =
                        _dbClient.queryObjectField(Project.class, "label",
                                new ArrayList<URI>(allMyProjects.keySet()));
                List<NamedElementQueryResultList.NamedElement> elements =
                        new ArrayList<NamedElementQueryResultList.NamedElement>(
                                project_list.size());
                for (Project p : project_list) {
                    elements.add(NamedElementQueryResultList.NamedElement.createElement(
                            p.getId(), p.getLabel()));
                }
                projects.setResult(elements.iterator());
            } else {
                // empty list
                projects.setResult(new ArrayList<NamedElementQueryResultList.NamedElement>()
                        .iterator());
            }
        }
        ProjectList list = new ProjectList();
        for (NamedElementQueryResultList.NamedElement el : projects) {
            list.getProjects().add(
                    toNamedRelatedResource(ResourceTypeEnum.PROJECT, el.getId(), el.getName()));
        }
        return list;
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param
     *            POST data containing the id list.
     * @prereq none
     * @brief List data of tenant resources
     * @return list of representations.
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public TenantOrgBulkRep getBulkResources(BulkIdParam param) {
        return (TenantOrgBulkRep) super.getBulkResources(param);
    }

    /**
     * Add the list of user mappings to the tenant. Check for conflicting mappings that
     * already exist.
     * 
     * @param tenant
     * @param userMappingParams
     */
    private void addUserMappings(TenantOrg tenant,
            List<UserMappingParam> userMappingParams, StorageOSUser user) {
        List<UserMapping> userMappings = UserMapping.fromParamList(userMappingParams);
        Map<String, Map<URI, List<UserMapping>>> domainUserMappingMap =
                new HashMap<String, Map<URI, List<UserMapping>>>();

        for (UserMapping userMapping : userMappings) {
            if (null == userMapping.getDomain() || userMapping.getDomain().isEmpty()) {
                throw APIException.badRequests.requiredParameterMissingOrEmpty("domain");
            }
            trimGroupAndDomainNames(userMapping);
            if (!authNProviderExistsForDomain(userMapping.getDomain())) {
                throw APIException.badRequests.invalidParameter("domain",
                        userMapping.getDomain());
            }
            String domain = userMapping.getDomain();
            Map<URI, List<UserMapping>> domainMappings = domainUserMappingMap.get(domain);
            if (null == domainMappings) {
                domainMappings = _permissionsHelper.getAllUserMappingsForDomain(domain);
                domainUserMappingMap.put(domain, domainMappings);
            }
            for (Entry<URI, List<UserMapping>> existingMappingEntry : domainMappings
                    .entrySet()) {
                if (!tenant.getId().equals(existingMappingEntry.getKey())) {
                    for (UserMapping existingMapping : existingMappingEntry.getValue()) {
                        if (userMapping.isMatch(existingMapping)) {
                            URI dupTenantURI = existingMappingEntry.getKey();
                            throw _permissionsHelper.userHasGivenRole(user, dupTenantURI,
                                    Role.TENANT_ADMIN) ? APIException.badRequests
                                    .userMappingDuplicatedInAnotherTenantExtended(
                                            userMapping.toString(),
                                            dupTenantURI.toString())
                                    : APIException.badRequests
                                            .userMappingDuplicatedInAnotherTenant(userMapping
                                                    .toString());
                        }
                    }
                }
            }

            // Now validate the user mapping. Now the user mapping is done
            // from the tenants service itself in order to differenticate
            // user group and groups in authentication provider.
            if (!isValidMapping(userMapping)) {
                throw APIException.badRequests.invalidParameter("user_mapping",
                        userMapping.toString());
            }
            tenant.addUserMapping(userMapping.getDomain(), userMapping.toString());
        }
    }

    /**
     * @param userMapping
     *            object for the tenant containing the domain, group and attribute
     *            information The method will trim spaces at the beginning and at the end
     *            of the domain and groups in the provided UserMapping object.
     * 
     *            the method also trim domain name from groups, if it matches domain.
     */
    private void trimGroupAndDomainNames(UserMapping userMapping) {
        if (null != userMapping) {
            userMapping.setDomain(userMapping.getDomain().trim());
            List<String> groupsIn = userMapping.getGroups();
            List<String> groupsOut = new ArrayList<String>();
            for (String igroup : groupsIn) {
                String groupName = igroup.trim();

                // if group contains @, potentially, the string after @ is its domain name,
                // if it matches authentication server, trim it.
                // if not match, then keep it, as it is part of the group name.
                String[] groupItems = igroup.split("@");
                if (groupItems.length > 1) {
                    String inputDomain = groupItems[groupItems.length - 1];
                    if (inputDomain.equalsIgnoreCase(userMapping.getDomain())) {
                        groupName = groupName.substring(0, groupName.lastIndexOf("@"));
                    }
                }

                groupsOut.add(groupName);
            }

            if (!groupsOut.isEmpty()) {
                userMapping.setGroups(groupsOut);
            }
        }

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
     * Check if a provider exists for the given domain
     * 
     * @param domain
     * @return
     */
    private boolean authNProviderExistsForDomain(String domain) {
        URIQueryResultList providers = new URIQueryResultList();
        try {
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getAuthnProviderDomainConstraint(domain), providers);
        } catch (DatabaseException ex) {
            _log.error(
                    "Could not query for authn providers to check for existing domain {}",
                    domain, ex.getStackTrace());
            throw ex;
        }

        // check if there is an AuthnProvider contains the given domain and not in disabled state
        boolean bExist = false;
        Iterator<URI> it = providers.iterator();
        while (it.hasNext()) {
            URI providerURI = it.next();
            AuthnProvider provider = _dbClient.queryObject(AuthnProvider.class, providerURI);
            if (provider != null && provider.getDisable() == false) {
                bExist = true;
                break;
            }
        }

        return bExist;
    }

    /**
     * Record Bourne Event for the completed operations
     * 
     * @param tenantId
     * @param targetId
     */
    private void recordTenantEvent(OperationTypeEnum opType, URI tenantId, URI targetId) {

        String type = opType.getEvType(true);
        String description = opType.getDescription();

        RecordableBourneEvent event = new RecordableBourneEvent(
                /* String */type,
                /* tenant id */tenantId,
                /* user id ?? */URI.create("ViPR-User"),
                /* project ID */null,
                /* CoS */null,
                /* service */EVENT_SERVICE_TYPE,
                /* resource id */targetId,
                /* description */description,
                /* timestamp */System.currentTimeMillis(),
                /* extensions */"",
                /* native guid */null,
                /* record type */RecordType.Event.name(),
                /* Event Source */EVENT_SERVICE_SOURCE,
                /* Operational Status codes */"",
                /* Operational Status Descriptions */"");
        try {
            _evtMgr.recordEvents(event);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException) {
                // CQ604367 --
                // print full stack trace of error. Problem is intermittent
                // and can not be reproduced easily. A NullPointerException
                // is getting thrown. This will code will help identify in
                // apisvc.log exactly where that occurs when it does.
                _log.error("Failed to record event. Event description: " + description
                        + ". Error: .", ex);
            } else {
                _log.error("Failed to record event. Event description: {}. Error: {}.",
                        description, ex);
            }
        }
        _log.info("opType: {} detail: {}", opType.toString(), type + ':' + description);
    }

    /**
     * Creates a new host for the tenant organization. Discovery is initiated
     * after the host is created.
     * <p>
     * This method is deprecated. Use /compute/hosts instead
     * 
     * @param tid
     *            the tenant organization id
     * @param createParam
     *            the parameter that has the type and attribute of the host to be created.
     * @prereq none
     * @deprecated use {@link HostService#createHost(HostCreateParam,Boolean)}
     * @brief Create tenant host
     * @return the host discovery async task representation.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/{id}/hosts")
    @Deprecated
    public TaskResourceRep createHost(@PathParam("id") URI tid,
            HostCreateParam createParam,
            @QueryParam("validate_connection") @DefaultValue("false") final Boolean validateConnection) {

        // This is mostly to validate the tenant
        TenantOrg tenant = getTenantById(tid, true);
        HostService hostService = _hostService;
        hostService.validateHostData(createParam, tid, null, validateConnection);

        // Create the host
        Host host = hostService.createNewHost(tenant, createParam);
        host.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        _dbClient.createObject(host);
        recordTenantResourceOperation(OperationTypeEnum.CREATE_HOST, tid, host);
        return hostService.doDiscoverHost(host);
    }

    /**
     * Lists the id and name for all the hosts that belong to the given tenant organization.
     * <p>
     * This method is deprecated. Use /compute/hosts instead
     * 
     * @param id the URN of a ViPR tenant organization
     * @prereq none
     * @deprecated use {@link HostService#listHosts()}
     * @brief List tenant hosts
     * @return a list of hosts that belong to the tenant organization.
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/hosts")
    @Deprecated
    public HostList listHosts(@PathParam("id") URI id) throws DatabaseException {
        // this call validates the tenant id
        getTenantById(id, false);
        // check the user permissions for this tenant org
        verifyAuthorizedInTenantOrg(id, getUserFromContext());
        // get all host children
        HostList list = new HostList();
        list.setHosts(map(ResourceTypeEnum.HOST,
                listChildren(id, Host.class, "label", "tenant")));
        return list;
    }

    /**
     * Creates a new vCenter for the tenant organization. Discovery is initiated
     * after the vCenter is created.
     * 
     * @param tid
     *            the tenant organization id
     * @param createParam
     *            the parameter that has the attributes of the vCenter to be created.
     * @prereq none
     * @brief Create tenant vCenter
     * @return the vCenter discovery async task.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/{id}/vcenters")
    public TaskResourceRep createVcenter(@PathParam("id") URI tid,
            VcenterCreateParam createParam,
            @QueryParam("validate_connection") @DefaultValue("false") final Boolean validateConnection) {

        // This validates the tenant
        TenantOrg tenant = getTenantById(tid, true);
        VcenterService service = _vcenterService;

        // validates the create param and validation is successful then creates and persist the vcenter
        Vcenter vcenter = service.createNewTenantVcenter(tenant, createParam, validateConnection);
        vcenter.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        _dbClient.createObject(vcenter);
        recordTenantResourceOperation(OperationTypeEnum.CREATE_VCENTER, tid, vcenter);

        return service.doDiscoverVcenter(vcenter);
    }

    /**
     * Lists the id and name for all the vCenters that belong to the given tenant organization.
     * 
     * @param id the URN of a ViPR tenant organization
     * @prereq none
     * @brief List tenant vCenters
     * @return a list of hosts that belong to the tenant organization.
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/vcenters")
    public VcenterList listVcenters(@PathParam("id") URI id) throws DatabaseException {
        // this call is for validating the tenant Id
        getTenantById(id, false);
        // check the user permissions for this tenant org
        verifyAuthorizedInTenantOrg(id, getUserFromContext());
        // get all children vcenters
        VcenterList list = new VcenterList();

        list.setVcenters(map(ResourceTypeEnum.VCENTER, listChildrenWithAcls(id, Vcenter.class, "label")));

        return list;
    }

    /**
     * Creates a new host cluster for the tenant organization.
     * 
     * @param tid
     *            the tenant organization id
     * @param createParam
     *            the parameter that has the type and attribute of the host to be created.
     * @prereq none
     * @brief Create host cluster for tenant
     * @return the host discovery async task representation.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/{id}/clusters")
    public ClusterRestRep createCluster(@PathParam("id") URI tid,
            ClusterCreateParam createParam) {
        // This is mostly to validate the tenant
        TenantOrg tenant = getTenantById(tid, true);
        ClusterService clusterService = _clusterService;
        clusterService.validateClusterData(createParam, tid, null, _dbClient);
        Cluster cluster = clusterService.createNewCluster(tenant, createParam);
        _dbClient.createObject(cluster);

        recordTenantResourceOperation(OperationTypeEnum.CREATE_CLUSTER, tid, cluster);

        return map(cluster);
    }

    /**
     * Lists the id and name for all the clusters that belong to the
     * tenant organization.
     * 
     * @param id the URN of a ViPR tenant organization
     * @prereq none
     * @brief List tenant clusters
     * @return a list of hosts that belong to the tenant organization.
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/clusters")
    public ClusterList listClusters(@PathParam("id") URI id) throws DatabaseException {
        // this called just for validation
        getTenantById(id, false);
        // check the user permissions for this tenant org
        verifyAuthorizedInTenantOrg(id, getUserFromContext());
        // get the children
        ClusterList list = new ClusterList();
        list.setClusters(map(ResourceTypeEnum.CLUSTER,
                listChildren(id, Cluster.class, "label", "tenant")));
        return list;
    }

    /**
     * Show quota and available capacity before quota is exhausted
     * 
     * @param id the URN of a ViPR Tenant.
     * @prereq none
     * @brief Show quota and available capacity
     * @return QuotaInfo Quota metrics.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SECURITY_ADMIN })
    @Path("/{id}/quota")
    public QuotaInfo getQuota(@PathParam("id") URI id) throws DatabaseException {
        TenantOrg tenant = getTenantById(id, true);
        return getQuota(tenant);
    }

    /**
     * Updates quota and available capacity before quota is exhausted
     * 
     * @param id the URN of a ViPR Tenant.
     * @param param new values for the quota
     * @prereq none
     * @brief Updates quota and available capacity
     * @return QuotaInfo Quota metrics.
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SECURITY_ADMIN })
    @Path("/{id}/quota")
    public QuotaInfo updateQuota(@PathParam("id") URI id, QuotaUpdateParam param)
            throws DatabaseException {

        TenantOrg tenant = getTenantById(id, true);

        tenant.setQuotaEnabled(param.getEnable());
        if (param.getEnable()) {
            long quota_gb =
                    (param.getQuotaInGb() != null) ? param.getQuotaInGb() : tenant
                            .getQuota();
            ArgValidator.checkFieldMinimum(quota_gb, 0, "quota_gb", "GB");

            // Verify that the quota of this "sub-tenant" does exceed the new quota for
            // its parent;
            if (!TenantOrg.isRootTenant(tenant)) {
                TenantOrg parent =
                        _dbClient.queryObject(TenantOrg.class, tenant.getParentTenant()
                                .getURI());
                if (parent.getQuotaEnabled()) {
                    long totalSubtenants =
                            CapacityUtils.totalSubtenantQuota(_dbClient, parent.getId());
                    totalSubtenants = totalSubtenants - tenant.getQuota() + quota_gb;
                    if (totalSubtenants > parent.getQuota()) {
                        throw APIException.badRequests
                                .invalidParameterTenantsQuotaInvalidatesParent(parent
                                        .getQuota());
                    }
                }
            }

            // Verify that the quota is consistent with quotas for subtenants.
            long totalSubtenants = CapacityUtils.totalSubtenantQuota(_dbClient, id);
            if (totalSubtenants > quota_gb) {
                throw APIException.badRequests
                        .invalidParameterTenantsQuotaExceedsSubtenants(quota_gb,
                                totalSubtenants);
            }

            // verify that the quota is consistentn with quotas for all projects.
            long totalProjects = CapacityUtils.totalProjectQuota(_dbClient, id);
            if (totalProjects > quota_gb) {
                throw APIException.badRequests
                        .invalidParameterTenantsQuotaExceedsProject(quota_gb,
                                totalProjects);
            }

            tenant.setQuota(quota_gb);
        }
        _dbClient.persistObject(tenant);

        return getQuota(tenant);
    }

    private QuotaInfo getQuota(TenantOrg tenant) {
        QuotaInfo quotaInfo = new QuotaInfo();
        double capacity =
                CapacityUtils.getTenantCapacity(_dbClient, tenant.getId());
        quotaInfo.setQuotaInGb(tenant.getQuota());
        quotaInfo.setEnabled(tenant.getQuotaEnabled());
        quotaInfo.setCurrentCapacityInGb((long) Math.ceil(capacity / CapacityUtils.GB));
        quotaInfo.setLimitedResource(DbObjectMapper.toNamedRelatedResource(tenant));
        return quotaInfo;
    }

    public void recordOperation(OperationTypeEnum opType, URI srctenantURI,
            TenantOrg tgttenant) {

        recordTenantEvent(opType, srctenantURI, tgttenant.getId());

        switch (opType) {
            case CREATE_TENANT:
                auditOp(opType, true, null, tgttenant.getLabel(), srctenantURI, tgttenant
                        .getId().toString());
                break;
            case UPDATE_TENANT:
                auditOp(opType, true, null, srctenantURI.toString(), tgttenant.getLabel());
                break;
            case DELETE_TENANT:
                auditOp(opType, true, null, tgttenant.getId().toString(),
                        tgttenant.getLabel());
                break;
            case REASSIGN_TENANT_ROLES:
                auditOp(opType, true, null, tgttenant.getId().toString(),
                        tgttenant.getLabel());
                break;
            default:
                _log.error("unrecognized tenant operation type");
        }
    }

    private void recordTenantResourceOperation(OperationTypeEnum opType, URI tenantUri,
            TenantResource tenantResource) {
        // TODO - Is an event required?
        auditOp(opType, true, null, tenantResource.auditParameters());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<TenantOrg> getResourceClass() {
        return TenantOrg.class;
    }

    @Override
    public TenantOrgBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<TenantOrg> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new TenantOrgBulkRep(BulkList.wrapping(_dbIterator,
                MapTenant.getInstance()));
    }

    @Override
    protected TenantOrgBulkRep queryFilteredBulkResourceReps(List<URI> ids) {

        Iterator<TenantOrg> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter filter =
                new BulkList.TenantFilter(getUserFromContext(), _permissionsHelper);
        return new TenantOrgBulkRep(BulkList.wrapping(_dbIterator,
                MapTenant.getInstance(), filter));
    }

    /**
     * Tenant is not a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.TENANT;
    }

    public static class TenantResRepFilter<E extends RelatedResourceRep> extends
            ResRepFilter<E> {
        public TenantResRepFilter(StorageOSUser user, PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(E resrep) {
            return isTenantAccessible(resrep.getId());
        }
    }

    /**
     * Get object specific permissions filter
     */
    @Override
    public ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(
            StorageOSUser user, PermissionsHelper permissionsHelper) {
        return new TenantResRepFilter(user, permissionsHelper);
    }

    /**
     * convert a tenants role assignments into a rest response
     * 
     * @param tenant
     * @return
     */
    private RoleAssignments getRoleAssignmentsResponse(TenantOrg tenant) {
        RoleAssignments roleAssignmentsResponse = new RoleAssignments();
        roleAssignmentsResponse.setAssignments(_permissionsHelper
                .convertToRoleAssignments(tenant.getRoleAssignments(), false));
        roleAssignmentsResponse.setSelfLink(getCurrentSelfLink());
        return roleAssignmentsResponse;
    }

    /**
     * Clear any tenant USE ACLs associated with the provided tenant id from the indicated CF
     * 
     * @param clazz CF type to clear of tenant ACLs
     * @param tenantId the tenant id
     * @param specifier optional specifier (e.g. block or file for VirtualPools)
     */
    private void clearTenantACLs(Class<? extends DataObjectWithACLs> clazz, URI tenantId, String specifier) {
        PermissionsKey permissionKey;
        if (StringUtils.isNotBlank(specifier)) {
            permissionKey = new PermissionsKey(PermissionsKey.Type.TENANT, tenantId.toString(), specifier);
        } else {
            permissionKey = new PermissionsKey(PermissionsKey.Type.TENANT, tenantId.toString());
        }
        URIQueryResultList resultURIs = new URIQueryResultList();
        Constraint aclConstraint = ContainmentPermissionsConstraint.Factory.getObjsWithPermissionsConstraint(
                permissionKey.toString(), clazz);
        _dbClient.queryByConstraint(aclConstraint, resultURIs);
        List<URI> ids = new ArrayList<URI>();
        for (URI result : resultURIs) {
            ids.add(result);
        }
        Iterator<? extends DataObjectWithACLs> objectIter = _dbClient.queryIterativeObjects(clazz, ids);
        if ((objectIter != null) && (objectIter.hasNext())) {
            List<DataObjectWithACLs> objectList = new ArrayList<DataObjectWithACLs>();
            while (objectIter.hasNext()) {
                objectList.add(objectIter.next());
            }
            for (DataObjectWithACLs object : objectList) {
                _log.info("Removing USE ACL for deleted subtenant {} from object {}", tenantId, object.getId());
                object.removeAcl(permissionKey.toString(), ACL.USE.toString());
            }
            _dbClient.updateAndReindexObject(objectList);
        }
    }

    /**
     * check if UserMapping is empty.
     * 
     * @param param
     * @return
     */
    private boolean isUserMappingEmpty(TenantUpdateParam param) {
        if (param == null || param.getUserMappingChanges() == null) {
            return true;
        }

        List<UserMappingParam> list = param.getUserMappingChanges().getAdd();
        if (list != null && !list.isEmpty()) {
            return false;
        }

        list = param.getUserMappingChanges().getRemove();
        if (list != null && !list.isEmpty()) {
            return false;
        }

        return true;
    }

    /**
     * Validate an attribute string
     * 
     * @param userMapping
     *            attribute string to validate
     * @return True if the attribute string is valid
     */
    public boolean isValidMapping(UserMapping userMapping) {
        if (CollectionUtils.isEmpty(userMapping.getGroups())) {
            return true;
        }

        for (String group : userMapping.getGroups()) {
            if (StringUtils.isBlank(group)) {
                _log.warn("Invalid group in the user mapping groups list");
                continue;
            }

            StorageOSPrincipal groupPrincipal = new StorageOSPrincipal();
            groupPrincipal.setType(StorageOSPrincipal.Type.Group);

            // First add the group with "@domain" suffix and after that
            // if we find that the group is a userGroup reset the
            // name to just the group name without "@domain" suffix.
            groupPrincipal.setName(group + "@" + userMapping.getDomain());

            List<UserGroup> userGroupList = _permissionsHelper.getAllUserGroupByLabel(group);
            if (!CollectionUtils.isEmpty(userGroupList)) {
                for (UserGroup userGroup : userGroupList) {
                    // If the returned userGroup's domain matches the userMapping
                    // domain, reset the groupPrincipal name to just group (without domain) name
                    // in order to treat the group as UserGroup.
                    if (userGroup != null &&
                            userGroup.getDomain().equalsIgnoreCase(userMapping.getDomain())) {
                        _log.debug("Group {} is considered as user group", group);
                        groupPrincipal.setName(group);
                    }
                }
            }

            if (!Validator.isValidPrincipal(groupPrincipal, null)) {
                return false;
            }
        }

        return true;
    }

    /**
     * make sure user-mapping's attributes are valid. all keys and values are not empty.
     * 
     * @param params
     */
    private void checkUserMappingAttribute(List<UserMappingParam> params) {
        if (params == null) {
            return;
        }

        for (UserMappingParam param : params) {
            List<UserMappingAttributeParam> attrs = param.getAttributes();
            if (attrs != null) {
                for (UserMappingAttributeParam attr : attrs) {
                    if (StringUtils.isEmpty(attr.getKey()) || CollectionUtils.isEmpty(attr.getValues())) {
                        throw BadRequestException.badRequests.userMappingAttributeIsEmpty();
                    }

                    for (String value : attr.getValues()) {
                        if (StringUtils.isEmpty(value)) {
                            throw BadRequestException.badRequests.userMappingAttributeIsEmpty();
                        }
                    }
                }
            }
        }
    }

    /**
     * SecurityAdmin has permission to create subtenant, or change a tenant's user mapping.
     * 
     * before persisting user-mapping change to database, this check makes sure current user, if he is SecurityAdmin,
     * will not be mapped out of Provider Tenant, and lose its security admin role.
     * 
     * @param tenant tenant, which is about to be created or modified
     */
    private void mapOutProviderTenantCheck(TenantOrg tenant) {

        // if the user don't have SecurityAdmin role, skip further check
        if (!_permissionsHelper.userHasGivenRole(
                (StorageOSUser) sc.getUserPrincipal(),
                null, Role.SECURITY_ADMIN)) {
            return;
        }

        String user = getUserFromContext().getName();
        UserInfoPage.UserTenantList userAndTenants = Validator.getUserTenants(user, tenant);

        if (null != userAndTenants) {
            List<UserInfoPage.UserTenant> userTenants = userAndTenants._userTenantList;

            if (CollectionUtils.isEmpty(userTenants)) {
                _log.error("User {} will not match any tenant after this user mapping change", user);
                throw APIException.badRequests.userMappingNotAllowed(user);
            } else if (userTenants.size() > 1) {
                _log.error("User {} will map to multiple tenants {} after this user mapping change", user, userTenants.toArray());
                throw APIException.badRequests.userMappingNotAllowed(user);
            } else {
                String tenantUri = userTenants.get(0)._id.toString();
                String providerTenantId = _permissionsHelper.getRootTenant().getId().toString();
                _log.debug("user will map to tenant: " + tenantUri);
                _log.debug("provider tenant ID: " + providerTenantId);

                if (!providerTenantId.equalsIgnoreCase(tenantUri)) {
                    _log.error("User {} will map to tenant {}, which is not provider tenant", user, tenant.getLabel());
                    throw APIException.badRequests.userMappingNotAllowed(user);
                }
            }

        }
    }
}

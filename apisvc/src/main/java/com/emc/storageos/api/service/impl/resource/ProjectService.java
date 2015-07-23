/*
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

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.storageos.db.common.VdcUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.mapper.functions.MapProject;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.authorization.PermissionsHelper.ACLInputFilter;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TypedRelatedResourceRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.auth.PrincipalsToValidate;
import com.emc.storageos.model.project.ProjectBulkRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.project.ProjectUpdateParam;
import com.emc.storageos.model.project.ResourceList;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.PermissionsKey;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.validator.StorageOSPrincipal;
import com.emc.storageos.security.validator.Validator;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

/**
 * Project resource implementation
 */
@Path("/projects")
@DefaultPermissions( read_roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
read_acls = {ACL.OWN, ACL.ALL},
write_roles = { Role.TENANT_ADMIN },
write_acls = {ACL.OWN, ACL.ALL})
public class ProjectService extends TaggedResource {
    private static final Logger _log = LoggerFactory.getLogger(ProjectService.class);
    // Constants for Events
    private static final String EVENT_SERVICE_TYPE = "project";
    private static final String EVENT_SERVICE_SOURCE = "ProjectService";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Autowired
    private RecordableEventManager _evtMgr;


    /**     
     * Get info for project including owner, parent project, and child projects
     * @param id the URN of a ViPR Project
     * @prereq none
     * @brief Show project
     * @return Project details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission( roles = {Role.SYSTEM_MONITOR, Role.TENANT_ADMIN}, acls = {ACL.ANY})
    public ProjectRestRep getProject(@PathParam("id") URI id) {
        Project project = queryResource(id);
        return map(project);
    }

    @Override
    protected Project queryResource(URI id) {
        Project project = getProjectById(id, false);
        return project;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        Project project = queryResource(id);
        return project.getTenantOrg().getURI();
    }

    /**     
     * Update info for project including project name and owner
     * @param projectUpdate Project update parameters
     * @param id the URN of a ViPR Project
     * @prereq none
     * @brief Update project
     * @return No data returned in response body
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission( roles = { Role.TENANT_ADMIN }, acls = {ACL.OWN})
    public Response updateProject(@PathParam("id") URI id, ProjectUpdateParam projectUpdate) {
        Project project = getProjectById(id, true);

        if (null != projectUpdate.getName() && !projectUpdate.getName().isEmpty() &&
                !project.getLabel().equalsIgnoreCase(projectUpdate.getName())) {
            checkForDuplicateName(projectUpdate.getName(), Project.class, project.getTenantOrg()
                    .getURI(), "tenantOrg", _dbClient);
            project.setLabel(projectUpdate.getName());
            NamedURI tenant  = project.getTenantOrg();
            if (tenant != null) {
                tenant.setName(projectUpdate.getName());
                project.setTenantOrg(tenant);
            }
        }

        if (null != projectUpdate.getOwner()
                && !projectUpdate.getOwner().isEmpty()
                && !projectUpdate.getOwner().equalsIgnoreCase(project.getOwner())) {
            StringBuilder error = new StringBuilder();
            if(!Validator.isValidPrincipal(new StorageOSPrincipal(projectUpdate.getOwner(),
                    StorageOSPrincipal.Type.User), project.getTenantOrg().getURI(), error)) {
                throw APIException.forbidden
                        .specifiedOwnerIsNotValidForProjectTenant(error.toString());
            }

            // in GEO scenario, root can't be assigned as project owner
            boolean isRootInGeo = (projectUpdate.getOwner().equalsIgnoreCase("root")
                && !VdcUtil.isLocalVdcSingleSite());

            if (isRootInGeo) {
                throw APIException.forbidden.specifiedOwnerIsNotValidForProjectTenant(
                        "in GEO scenario, root can't be assigned as project owner"
                );
            }

            // set owner acl
            project.removeAcl(new PermissionsKey(PermissionsKey.Type.SID, project.getOwner(),
                    project.getTenantOrg().getURI()).toString(), ACL.OWN.toString());
            project.setOwner(projectUpdate.getOwner());
            // set owner acl
            project.addAcl(new PermissionsKey(PermissionsKey.Type.SID, project.getOwner(),
                    project.getTenantOrg().getURI()).toString(),
                    ACL.OWN.toString());
        }

        _dbClient.updateAndReindexObject(project);

        recordOperation(OperationTypeEnum.UPDATE_PROJECT, true, project);
        return Response.ok().build();
    }

    /**     
     * List resources in project
     * @param id the URN of a ViPR Project
     * @prereq none
     * @brief List project resources
     * @return List of resources
     */
    @SuppressWarnings("unchecked")
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/resources")
    @CheckPermission( roles = {Role.SYSTEM_MONITOR, Role.TENANT_ADMIN}, acls = {ACL.ANY})
    public ResourceList getResourceList(@PathParam("id") URI id) {
        Project project = getProjectById(id, false);

        QueryResultList<TypedRelatedResourceRep> file = new QueryResultList<TypedRelatedResourceRep>() {
            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri) {
                TypedRelatedResourceRep resource = new TypedRelatedResourceRep();
                resource.setId(uri);
                resource.setType(ResourceTypeEnum.FILE);
                return resource;
            }

            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri, String name, UUID timestamp) {
                TypedRelatedResourceRep resource = new TypedRelatedResourceRep();
                resource.setId(uri);
                resource.setType(ResourceTypeEnum.FILE);
                resource.setName(name);
                return resource;
            }

            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri, Object entry) {
               return  createQueryHit(uri);
            }

        };

        QueryResultList<TypedRelatedResourceRep> volume = new QueryResultList<TypedRelatedResourceRep>() {
            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri) {
                TypedRelatedResourceRep resource = new TypedRelatedResourceRep();
                resource.setId(uri);
                resource.setType(ResourceTypeEnum.VOLUME);
                return resource;
            }

            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri, String name, UUID timestamp) {
                TypedRelatedResourceRep resource = new TypedRelatedResourceRep();
                resource.setId(uri);
                resource.setType(ResourceTypeEnum.VOLUME);
                resource.setName(name);
                return resource;
            }

            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri, Object entry) {
                return  createQueryHit(uri);
            }
        };

        QueryResultList<TypedRelatedResourceRep> exportgroup = new QueryResultList<TypedRelatedResourceRep>() {
            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri) {
                TypedRelatedResourceRep resource = new TypedRelatedResourceRep();
                resource.setId(uri);
                resource.setType(ResourceTypeEnum.EXPORT_GROUP);
                return resource;
            }

            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri, String name, UUID timestamp) {
                TypedRelatedResourceRep resource = new TypedRelatedResourceRep();
                resource.setId(uri);
                resource.setType(ResourceTypeEnum.EXPORT_GROUP);
                resource.setName(name);
                return resource;
            }

            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri, Object entry) {
                return  createQueryHit(uri);
            }

        };

        QueryResultList<TypedRelatedResourceRep> blockSnapshot = new QueryResultList<TypedRelatedResourceRep>() {
            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri) {
                TypedRelatedResourceRep resource = new TypedRelatedResourceRep();
                resource.setId(uri);
                resource.setType(ResourceTypeEnum.BLOCK_SNAPSHOT);
                return resource;
            }

            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri, String name, UUID timestamp) {
                TypedRelatedResourceRep resource = new TypedRelatedResourceRep();
                resource.setId(uri);
                resource.setType(ResourceTypeEnum.BLOCK_SNAPSHOT);
                resource.setName(name);
                return resource;
            }

            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri, Object entry) {
                return  createQueryHit(uri);
            }
        };

        QueryResultList<TypedRelatedResourceRep> fileSnapshot = new QueryResultList<TypedRelatedResourceRep>() {
            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri) {
                TypedRelatedResourceRep resource = new TypedRelatedResourceRep();
                resource.setId(uri);
                resource.setType(ResourceTypeEnum.FILE_SNAPSHOT);
                return resource;
            }

            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri, String name, UUID timestamp) {
                TypedRelatedResourceRep resource = new TypedRelatedResourceRep();
                resource.setId(uri);
                resource.setType(ResourceTypeEnum.FILE_SNAPSHOT);
                resource.setName(name);
                return resource;
            }

            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri, Object entry) {
                return  createQueryHit(uri);
            }
        };

        QueryResultList<TypedRelatedResourceRep> protectionSet = new QueryResultList<TypedRelatedResourceRep>() {
            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri) {
                TypedRelatedResourceRep resource = new TypedRelatedResourceRep();
                resource.setId(uri);
                resource.setType(ResourceTypeEnum.PROTECTION_SET);
                return resource;
            }

            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri, String name, UUID timestamp) {
                TypedRelatedResourceRep resource = new TypedRelatedResourceRep();
                resource.setId(uri);
                resource.setType(ResourceTypeEnum.PROTECTION_SET);
                resource.setName(name);
                return resource;
            }

            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri, Object entry) {
                return  createQueryHit(uri);
            }
        };

        QueryResultList<TypedRelatedResourceRep> blockConsistencySet = new QueryResultList<TypedRelatedResourceRep>() {
            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri) {
                TypedRelatedResourceRep resource = new TypedRelatedResourceRep();
                resource.setId(uri);
                resource.setType(ResourceTypeEnum.BLOCK_CONSISTENCY_GROUP);
                return resource;
            }

            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri, String name, UUID timestamp) {
                TypedRelatedResourceRep resource = new TypedRelatedResourceRep();
                resource.setId(uri);
                resource.setType(ResourceTypeEnum.BLOCK_CONSISTENCY_GROUP);
                resource.setName(name);
                return resource;
            }

            @Override
            public TypedRelatedResourceRep createQueryHit(URI uri, Object entry) {
                return  createQueryHit(uri);
            }
        };
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getProjectVolumeConstraint(project.getId()), volume);
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getProjectFileshareConstraint(project.getId()), file);
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getProjectExportGroupConstraint(project.getId()), exportgroup);
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getProjectBlockSnapshotConstraint(project.getId()), blockSnapshot);
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getProjectFileSnapshotConstraint(project.getId()), fileSnapshot);

        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getProjectProtectionSetConstraint(project.getId()), protectionSet);

        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getProjectBlockConsistencyGroupConstraint(project.getId()), blockConsistencySet);

        ResourceList list = new ResourceList();
        list.setResources(new ChainedList<TypedRelatedResourceRep>(file.iterator(),
                volume.iterator(), exportgroup.iterator(), blockSnapshot.iterator(),
                fileSnapshot.iterator(), file.iterator(), volume.iterator(),
                exportgroup.iterator(), protectionSet.iterator(), blockConsistencySet.iterator()));
        return list;
    }

    /**     
     * Deactivates the project.
     * When a project is deleted it will move to a "marked for deletion" state.  Once in this state,
     * new resources or child projects may no longer be created in the project.
     * The project will be permanently deleted once all its references of type
     * ExportGroup, FileSystem, KeyPool, KeyPoolInfo, Volume are deleted.
     * @prereq none
     * @param id the URN of a ViPR Project
     * @brief Deactivate project
     * @return No data returned in response body
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission( roles = {Role.TENANT_ADMIN}, acls = {ACL.OWN})
    public Response deactivateProject(@PathParam("id") URI id) {
        Project project = getProjectById(id, true);
        ArgValidator.checkReference(Project.class, id, checkForDelete(project));
        _dbClient.markForDeletion(project);

        recordOperation(OperationTypeEnum.DELETE_PROJECT, true, project);
        return Response.ok().build();
    }

    /**
     * Filter class for validating input args for project ACLs
     */
    public class ProjectACLFilter extends ACLInputFilter {
        private final TenantOrg _tenant;

        private List<String> _groups;
        private List<String> _users;

        public ProjectACLFilter(TenantOrg tenant) {
            _tenant = tenant;
        }

        @Override
        public PermissionsKey getPermissionKeyForEntry(ACLEntry entry) throws SecurityException {
            PermissionsKey key;
            StorageOSPrincipal principal = new StorageOSPrincipal();
            if (entry.getGroup() != null) {
                String group = entry.getGroup();
                key = new PermissionsKey (PermissionsKey.Type.GROUP, group, _tenant.getId());
                principal.setName(group);
                principal.setType(StorageOSPrincipal.Type.Group);
            } else if (entry.getSubjectId() != null) {
                key = new PermissionsKey (PermissionsKey.Type.SID, entry.getSubjectId(), _tenant.getId());
                principal.setName(entry.getSubjectId());
                principal.setType(StorageOSPrincipal.Type.User);
            } else {
                throw APIException.badRequests.invalidEntryForProjectACL();
            }

            return key;
        }

        @Override
        public boolean isValidACL(String ace) {
            return (_permissionsHelper.isProjectACL(ace) &&
                    !ace.equalsIgnoreCase(ACL.OWN.toString()));
        }

        @Override
        public void validate() {
            PrincipalsToValidate principalsToValidate = new PrincipalsToValidate();
            principalsToValidate.setGroups(_groups);
            principalsToValidate.setUsers(_users);
            principalsToValidate.setTenantId(_tenant.getId().toString());
            StringBuilder error = new StringBuilder();
            if (!Validator.validatePrincipals(principalsToValidate, error)) {
                throw APIException.badRequests.invalidRoleAssignments(error.toString());
            }
        }

        @Override
        protected void addPrincipalToList(PermissionsKey key) {
            switch (key.getType()) {
                case GROUP:
                    _groups.add(key.getValue());
                    break;
                case SID:
                    _users.add(key.getValue());
                    break;
                case TENANT:
                default:
                    break;
            }
        }

        @Override
        protected void initLists() {
            _groups = new ArrayList<String>();
            _users = new ArrayList<String>();
        }
    
    }

    /**     
     * Get project ACL
     * @param id the URN of a ViPR Project
     * @prereq none
     * @brief Show project ACL
     * @return ACL Assignment details
     */
    @GET
    @Path("/{id}/acl")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission( roles = {Role.SECURITY_ADMIN, Role.TENANT_ADMIN}, acls = {ACL.OWN})
    public ACLAssignments getRoleAssignments(@PathParam("id") URI id) {
        return getRoleAssignmentsResponse(id);
    }

    /**     
     * Add or remove individual ACL entry(s)
     * @param changes ACL assignment changes. Request body must include at least one add or remove operation
     * @param id the URN of a ViPR Project
     * @prereq none
     * @brief Add or remove project ACL entries
     * @return No data returned in response body
     */
    @PUT
    @Path("/{id}/acl")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission( roles = {Role.SECURITY_ADMIN, Role.TENANT_ADMIN}, acls = {ACL.OWN} , block_proxies = true)
    public ACLAssignments updateRoleAssignments(@PathParam("id") URI id,
            ACLAssignmentChanges changes) {
        Project project = getProjectById(id, true);
        TenantOrg tenant = _permissionsHelper.getObjectById(project.getTenantOrg().getURI(), TenantOrg.class);
        _permissionsHelper.updateACLs(project, changes, new ProjectACLFilter(tenant));
        _dbClient.updateAndReindexObject(project);

        recordProjectEvent(project, OperationTypeEnum.MODIFY_PROJECT_ACL, true);

        auditOp(OperationTypeEnum.MODIFY_PROJECT_ACL, true, null, project.getId()
                .toString(), project.getLabel(), changes);
        return getRoleAssignmentsResponse(id);
    }

    private ACLAssignments getRoleAssignmentsResponse(URI id) {
        Project project = getProjectById(id, false);
        ACLAssignments response = new ACLAssignments();
        response.setAssignments(_permissionsHelper.convertToACLEntries(project.getAcls()));
        return response;
    }

    /**     
     * Retrieve resource representations based on input ids.
     * @prereq none
     * @param param POST data containing the id list.
     * @brief List data of project resources
     * @return list of representations.     
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Override
    public ProjectBulkRep getBulkResources(BulkIdParam param) {
        return (ProjectBulkRep) super.getBulkResources(param);
    }

    /**
     * Show quota and available capacity before quota is exhausted     
     * @prereq none
     * @param id the URN of a ViPR project.
     * @brief Show quota and available capacity
     * @return QuotaInfo Quota metrics.
     */

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    @Path("/{id}/quota")
    public QuotaInfo getQuota(@PathParam("id") URI id) throws DatabaseException {
        Project project = getProjectById(id, true);
        return  getQuota(project);
    }

    /**
     * Updates quota and available capacity before quota is exhausted     
     * @param id the URN of a ViPR Project.
     * @param param   new values for the quota
     * @prereq none
     * @brief Updates quota and available capacity
     * @return QuotaInfo Quota metrics.
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/{id}/quota")
    public QuotaInfo updateQuota(@PathParam("id") URI id,
            QuotaUpdateParam param) throws DatabaseException {

        Project project = getProjectById(id, true);

        project.setQuotaEnabled(param.getEnable());
        if( param.getEnable())  {
            long quota_gb = (param.getQuotaInGb() != null) ? param.getQuotaInGb() : project.getQuota();
            ArgValidator.checkFieldMinimum(quota_gb, 0, "quota_gb","GB");

            // Verify that the quota of this project does not exit quota for its tenant;
            TenantOrg  tenant = _dbClient.queryObject(TenantOrg.class,project.getTenantOrg().getURI());
            if(tenant.getQuotaEnabled()){
                long totalProjects =  CapacityUtils.totalProjectQuota(_dbClient,tenant.getId()) -
                        project.getQuota() +
                        quota_gb;
                if(totalProjects > tenant.getQuota()) {
                    throw APIException.badRequests.invalidParameterProjectQuotaInvalidatesTenantQuota(tenant.getQuota());
                }
            }

            project.setQuota(quota_gb);
        }
        _dbClient.persistObject(project);

        return  getQuota(project);
    }

    private QuotaInfo getQuota(Project project) {
        QuotaInfo quotaInfo = new  QuotaInfo();
        double capacity = CapacityUtils.getProjectCapacity(_dbClient,project.getId());
        quotaInfo.setQuotaInGb(project.getQuota());
        quotaInfo.setEnabled(project.getQuotaEnabled());
        quotaInfo.setCurrentCapacityInGb((long) Math.ceil(capacity / CapacityUtils.GB));
        quotaInfo.setLimitedResource( DbObjectMapper.toNamedRelatedResource(project) );
        return quotaInfo;
    }

    /**
     * Get project object from id
     * @param id the URN of a ViPR Project
     * @return
     */
    private Project getProjectById(URI id, boolean checkInactive) {
        if (id == null) {
            return null;
        }

        Project ret = _permissionsHelper.getObjectById(id, Project.class);

        ArgValidator.checkEntity(ret, id, isIdEmbeddedInURL(id), checkInactive);
        return ret;
    }

    /**
     * Create an event based on the project
     * @param project for which the event is about
     * @param opType Type of event such as ProjectCreated, ProjectDeleted
     * @param opStatus
     */
    public void recordProjectEvent(Project project, OperationTypeEnum opType,
            Boolean opStatus) {
        String type = opType.getEvType(opStatus);
        String description = opType.getDescription();
        _log.info("opType: {} detail: {}", opType.toString(), type + ':' + description);

        RecordableBourneEvent event = new RecordableBourneEvent(
                type,
                project.getTenantOrg().getURI(),
                URI.create(getUserFromContext().getName()),
                project.getId(),
                null,
                EVENT_SERVICE_TYPE,
                project.getId(),
                description,
                System.currentTimeMillis(),
                "" , // extensions
                "",
                RecordType.Event.name(),
                EVENT_SERVICE_SOURCE,
                "",
                ""
                );
        try {
            _evtMgr.recordEvents(event);
        } catch(Exception ex ) {
            _log.error("Failed to record event. Event description: {}. Error: {}.",  description, ex);
        }
    }


    public void recordOperation(OperationTypeEnum opType, boolean opStatus,
            Project project) {

        recordProjectEvent(project, opType, opStatus);
        switch (opType) {
            case UPDATE_PROJECT:
                auditOp(opType, opStatus, null, project.getId().toString(),
                        project.getLabel(), project.getOwner());
                break;
            case DELETE_PROJECT:
                auditOp(opType, opStatus, null, project.getId().toString(),
                        project.getLabel());
                break;
            case REASSIGN_PROJECT_ACL:
                auditOp(opType, opStatus, null, project.getId().toString(),
                        project.getLabel());
                break;
            default:
                _log.error("unrecognized project opType");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<Project> getResourceClass() {
        return Project.class;
    }


    @Override
    public ProjectBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<Project> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new ProjectBulkRep(BulkList.wrapping(_dbIterator, MapProject.getInstance()));
    }

    @Override
    protected ProjectBulkRep queryFilteredBulkResourceReps(
            List<URI> ids) {

        Iterator<Project> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.ProjectFilter(getUserFromContext(), _permissionsHelper);
        return new ProjectBulkRep(BulkList.wrapping(_dbIterator, MapProject.getInstance(), filter));
    }

    /**
     * Project is not a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected ResourceTypeEnum getResourceType(){
        return ResourceTypeEnum.PROJECT;
    }

    public static class ProjectResRepFilter<E extends RelatedResourceRep>
    extends ResRepFilter<E> {
        public ProjectResRepFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(E resrep) {
            boolean ret = false;
            URI id = resrep.getId();
            Project project = _permissionsHelper.getObjectById(id, Project.class);
            if (project == null) {
                return false;
            }
            URI tenantUri = project.getTenantOrg().getURI();

            ret = isTenantAccessible(tenantUri);
            if (!ret) {
                ret = isProjectAccessible(id);
            }
            return ret;
        }

    }

    /**
     * Get object specific permissions filter
     */
    @Override
    public ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper)
    {
        return new ProjectResRepFilter(user, permissionsHelper);
    }
}

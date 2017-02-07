/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toLink;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.FilePolicyMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.FilePolicyMapper;
import com.emc.storageos.api.mapper.functions.MapFilePolicy;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.utils.FilePolicyServiceUtils;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FilePolicy.FileReplicationType;
import com.emc.storageos.db.client.model.FilePolicy.SnapshotExpireType;
import com.emc.storageos.db.client.model.FileReplicationTopology;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.PolicyStorageResource;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationUtils;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.model.file.policy.FilePolicyAssignParam;
import com.emc.storageos.model.file.policy.FilePolicyBulkRep;
import com.emc.storageos.model.file.policy.FilePolicyCreateParam;
import com.emc.storageos.model.file.policy.FilePolicyCreateResp;
import com.emc.storageos.model.file.policy.FilePolicyListRestRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.emc.storageos.model.file.policy.FilePolicyStorageResourceRestRep;
import com.emc.storageos.model.file.policy.FilePolicyStorageResources;
import com.emc.storageos.model.file.policy.FilePolicyUnAssignParam;
import com.emc.storageos.model.file.policy.FilePolicyUpdateParam;
import com.emc.storageos.model.file.policy.FileReplicationPolicyParam;
import com.emc.storageos.model.file.policy.FileReplicationTopologyParam;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyExpireParam;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.volumecontroller.FileController;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;

/**
 * @author jainm15
 */
@Path("/file/file-policies")
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.SYSTEM_ADMIN,
        Role.RESTRICTED_SYSTEM_ADMIN })
public class FilePolicyService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(FilePolicyService.class);

    protected static final String EVENT_SERVICE_SOURCE = "FilePolicyService";

    private static final String EVENT_SERVICE_TYPE = "FilePolicy";

    // File service implementations
    static volatile private Map<String, FileServiceApi> _fileServiceApis;

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    private NetworkService networkSvc;

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    public static void setFileServiceApis(Map<String, FileServiceApi> _fileServiceApis) {
        FilePolicyService._fileServiceApis = _fileServiceApis;
    }

    public static FileServiceApi getDefaultFileServiceApi() {
        return _fileServiceApis.get("default");
    }

    @Override
    protected FilePolicy queryResource(URI id) {
        ArgValidator.checkUri(id);
        FilePolicy filePolicy = _permissionsHelper.getObjectById(id, FilePolicy.class);
        ArgValidator.checkEntityNotNull(filePolicy, id, isIdEmbeddedInURL(id));
        return filePolicy;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.FILE_POLICY;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class<FilePolicy> getResourceClass() {
        return FilePolicy.class;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public FilePolicyBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<FilePolicy> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.FilePolicyResourceFilter(getUserFromContext(), _permissionsHelper);
        return new FilePolicyBulkRep(BulkList.wrapping(_dbIterator,
                MapFilePolicy.getInstance(_dbClient), filter));
    }

    @Override
    public FilePolicyBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        return queryBulkResourceReps(ids);
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param POST data containing the id list.
     * @brief List of file policies of given ids.
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public FilePolicyBulkRep getBulkResources(BulkIdParam param) {
        return (FilePolicyBulkRep) super.getBulkResources(param);
    }

    /**
     * @brief Create File Snapshot, Replication Policy
     * 
     * @param param FilePolicyParam
     * @return FilePolicyCreateResp
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public FilePolicyCreateResp createFilePolicy(FilePolicyCreateParam param) {
        FilePolicyCreateResp resp = new FilePolicyCreateResp();
        // Make policy name as mandatory field
        ArgValidator.checkFieldNotNull(param.getPolicyName(), "policyName");

        // Make apply at as mandatory field
        ArgValidator.checkFieldNotNull(param.getPolicyName(), "apply_at");

        // Check for duplicate policy name
        if (param.getPolicyName() != null && !param.getPolicyName().isEmpty()) {
            checkForDuplicateName(param.getPolicyName(), FilePolicy.class);
        }

        // check policy type is valid or not
        ArgValidator.checkFieldValueFromEnum(param.getPolicyType(), "policy_type",
                EnumSet.allOf(FilePolicyType.class));

        // check the policy apply level is valid or not
        ArgValidator.checkFieldValueFromEnum(param.getApplyAt(), "apply_at",
                EnumSet.allOf(FilePolicyApplyLevel.class));

        _log.info("file policy creation started -- ");
        if (param.getPolicyType().equals(FilePolicyType.file_replication.name())) {
            return createFileReplicationPolicy(param);

        } else if (param.getPolicyType().equals(FilePolicyType.file_snapshot.name())) {
            return createFileSnapshotPolicy(param);
        }
        return resp;
    }

    /**
     * Gets the ids and self links of all file policies.
     * 
     * @brief List file policies
     * @return A list of file policy reference specifying the ids and self links.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public FilePolicyListRestRep getFilePolicies() {
        return getFilePoliciesForGivenUser();
    }

    private boolean userHasTenantAdminRoles() {
        StorageOSUser user = getUserFromContext();

        if (_permissionsHelper.userHasGivenRole(user,
                null, Role.TENANT_ADMIN)) {
            return true;
        }
        return false;
    }

    private boolean userHasSystemAdminRoles() {
        StorageOSUser user = getUserFromContext();

        if (_permissionsHelper.userHasGivenRole(user,
                null, Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN)) {
            return true;
        }
        return false;
    }

    protected FilePolicyListRestRep getFilePoliciesForGivenUser() {

        FilePolicyListRestRep filePolicyList = new FilePolicyListRestRep();
        List<URI> ids = _dbClient.queryByType(FilePolicy.class, true);
        List<FilePolicy> filePolicies = _dbClient.queryObject(FilePolicy.class, ids);

        StorageOSUser user = getUserFromContext();
        // full list if role is {Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR} AND no tenant restriction from input
        // else only return the list, which input tenant has access.
        if (_permissionsHelper.userHasGivenRole(user, null, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR)) {
            for (FilePolicy filePolicy : filePolicies) {
                filePolicyList.add(toNamedRelatedResource(filePolicy, filePolicy.getFilePolicyName()));
            }
        } else {
            // otherwise, filter by only authorized to use
            URI tenant = null;

            tenant = URI.create(user.getTenantId());

            Set<FilePolicy> policySet = new HashSet<FilePolicy>();
            for (FilePolicy filePolicy : filePolicies) {
                if (_permissionsHelper.tenantHasUsageACL(tenant, filePolicy)) {
                    policySet.add(filePolicy);
                }
            }

            // Also adding vpools which sub-tenants of the user have access to.
            List<URI> subtenants = _permissionsHelper.getSubtenantsWithRoles(user);
            for (FilePolicy filePolicy : filePolicies) {
                if (_permissionsHelper.tenantHasUsageACL(subtenants, filePolicy)) {
                    policySet.add(filePolicy);
                }
            }

            for (FilePolicy filePolicy : policySet) {
                filePolicyList.add(toNamedRelatedResource(filePolicy, filePolicy.getFilePolicyName()));
            }
        }

        return filePolicyList;

    }

    /**
     * @brief Get details of a file policy.
     * 
     * @param id of the file policy.
     * @return File policy information.
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public FilePolicyRestRep getFilePolicy(@PathParam("id") URI id) {

        _log.info("Request recieved to get the file policy of id: {}", id);
        FilePolicy filepolicy = queryResource(id);
        ArgValidator.checkEntity(filepolicy, id, true);
        return map(filepolicy, _dbClient);
    }

    /**
     * @brief Delete file policy.
     * @param id of the file policy.
     * @return
     */
    @DELETE
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteFilePolicy(@PathParam("id") URI id) {

        FilePolicy filepolicy = queryResource(id);
        ArgValidator.checkEntity(filepolicy, id, true);

        ArgValidator.checkReference(FilePolicy.class, id, checkForDelete(filepolicy));

        StringSet assignedResources = filepolicy.getAssignedResources();

        if (assignedResources != null && !assignedResources.isEmpty()) {
            _log.error("Delete file pocicy failed because the policy has associacted resources");
            throw APIException.badRequests.failedToDeleteFilePolicy(filepolicy.getLabel(), "This policy has assigned resources.");
        }

        _dbClient.markForDeletion(filepolicy);

        auditOp(OperationTypeEnum.DELETE_FILE_POLICY, true, null, filepolicy.getId().toString(),
                filepolicy.getLabel());
        return Response.ok().build();
    }

    /**
     * @brief Assign File Policy to vpool, project, file system
     * 
     * @param id of the file policy.
     * @param param FilePolicyAssignParam
     * @return FilePolicyAssignResp
     */
    @POST
    @Path("/{id}/assign-policy")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public TaskResourceRep assignFilePolicy(@PathParam("id") URI id, FilePolicyAssignParam param) {

        ArgValidator.checkFieldUriType(id, FilePolicy.class, "id");
        FilePolicy filepolicy = this._dbClient.queryObject(FilePolicy.class, id);
        ArgValidator.checkEntity(filepolicy, id, true);

        String applyAt = filepolicy.getApplyAt();

        FilePolicyApplyLevel appliedAt = FilePolicyApplyLevel.valueOf(applyAt);
        switch (appliedAt) {
            case vpool:
                return assignFilePolicyToVpool(param, filepolicy);
            case project:
                return assignFilePolicyToProject(param, filepolicy);
            default:
                throw APIException.badRequests.invalidFilePolicyApplyLevel(appliedAt.name());
        }
    }

    /**
     * @brief Unassign File Policy from vpool, project, file system.
     * @param id of the file policy.
     * @param FilePolicyUnAssignParam
     * @return TaskResourceRep
     */
    @POST
    @Path("/{id}/unassign-policy")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public TaskResourceRep unassignFilePolicy(@PathParam("id") URI id, FilePolicyUnAssignParam param) {

        _log.info("Unassign File Policy :{}  request received.", id);
        String task = UUID.randomUUID().toString();

        ArgValidator.checkFieldUriType(id, FilePolicy.class, "id");
        FilePolicy filepolicy = this._dbClient.queryObject(FilePolicy.class, id);
        ArgValidator.checkEntity(filepolicy, id, true);
        StringBuilder errorMsg = new StringBuilder();

        if (filepolicy.getAssignedResources() == null || filepolicy.getAssignedResources().isEmpty()) {
            _log.info("File Policy: " + id + " doesn't have any assigned resources.");
            Operation op = _dbClient.createTaskOpStatus(FilePolicy.class, filepolicy.getId(),
                    task, ResourceOperationTypeEnum.UNASSIGN_FILE_POLICY);
            op.setDescription("unassign File Policy from resources ");
            _dbClient.ready(FilePolicy.class, filepolicy.getId(), task);
            return toTask(filepolicy, task, op);
        }

        ArgValidator.checkFieldNotNull(param.getUnassignfrom(), "unassign_from");
        Set<URI> unassignFrom = param.getUnassignfrom();
        if (unassignFrom != null) {
            for (URI uri : unassignFrom) {
                canUserUnAssignPolicyAtGivenLevel(filepolicy, uri);
                if (!filepolicy.getAssignedResources().contains(uri.toString())) {
                    errorMsg.append("Provided resource URI is either being not assigned to the file policy:" + filepolicy.getId()
                            + " or it is a invalid URI");
                    _log.error(errorMsg.toString());
                    throw APIException.badRequests.invalidFilePolicyUnAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
                }
            }
        }
        Operation op = _dbClient.createTaskOpStatus(FilePolicy.class, filepolicy.getId(),
                task, ResourceOperationTypeEnum.UNASSIGN_FILE_POLICY);
        op.setDescription("unassign File Policy from resources ");
        FileOrchestrationController controller = getController(FileOrchestrationController.class,
                FileOrchestrationController.FILE_ORCHESTRATION_DEVICE);
        try {
            controller.unassignFilePolicy(filepolicy.getId(), unassignFrom, task);
            auditOp(OperationTypeEnum.UNASSIGN_FILE_POLICY, true, "BEGIN", filepolicy.getId().toString(),
                    filepolicy.getLabel());

        } catch (BadRequestException e) {
            op = _dbClient.error(FilePolicy.class, filepolicy.getId(), task, e);
            _log.error("Error Unassigning File policy {}, {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            _log.error("Error Unassigning Files Policy {}, {}", e.getMessage(), e);
            throw APIException.badRequests.unableToProcessRequest(e.getMessage());
        }
        return toTask(filepolicy, task, op);
    }

    /**
     * Add or remove individual File Policy ACL entry(s). Request body must include at least one add or remove operation.
     * 
     * @param id the URN of a ViPR File Policy
     * @param changes ACL assignment changes
     * @brief Add or remove ACL entries from file store VirtualPool
     * @return No data returned in response body
     */
    @PUT
    @Path("/{id}/acl")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    public ACLAssignments updateAcls(@PathParam("id") URI id,
            ACLAssignmentChanges changes) {

        FilePolicy policy = queryResource(id);
        ArgValidator.checkEntityNotNull(policy, id, isIdEmbeddedInURL(id));
        _permissionsHelper.updateACLs(policy, changes,
                new PermissionsHelper.UsageACLFilter(_permissionsHelper));
        _dbClient.updateObject(policy);
        return getAclsOnPolicy(id);
    }

    /**
     * Get File Policy ACLs
     * 
     * @param id the URI of a ViPR FilePolicy
     * @brief Show ACL entries for File policy
     * @return ACL Assignment details
     */
    @GET
    @Path("/{id}/acl")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ACLAssignments getAcls(@PathParam("id") URI id) {
        return getAclsOnPolicy(id);
    }

    protected ACLAssignments getAclsOnPolicy(URI id) {
        FilePolicy policy = queryResource(id);
        ArgValidator.checkEntityNotNull(policy, id, isIdEmbeddedInURL(id));
        ACLAssignments response = new ACLAssignments();
        response.setAssignments(_permissionsHelper.convertToACLEntries(policy.getAcls()));
        return response;
    }

    /**
     * @brief Update the file policy
     * 
     * @param id the URI of a ViPR FilePolicy
     * @param param FilePolicyUpdateParam
     * @return FilePolicyCreateResp
     */
    @PUT
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep updateFilePolicy(@PathParam("id") URI id, FilePolicyUpdateParam param) {
        ArgValidator.checkFieldUriType(id, FilePolicy.class, "id");
        FilePolicy filePolicy = this._dbClient.queryObject(FilePolicy.class, id);
        ArgValidator.checkEntity(filePolicy, id, true);

        if (filePolicy.getPolicyStorageResources() != null && !filePolicy.getPolicyStorageResources().isEmpty()) {
            _log.info("validating file policy update parameters.");

            validateFilePolicyUpdateParams(filePolicy, param);
            // if No storage resource, update the original policy template!!
            _dbClient.updateObject(filePolicy);

            _log.info("Updating the storage system policy started..");
            return updateStorageSystemFileProtectionPolicy(filePolicy, param);

        } else {
            _log.info("validate and update file policy parameters started -- ");
            if (filePolicy.getFilePolicyType().equals(FilePolicyType.file_replication.name())) {
                updateFileReplicationPolicy(filePolicy, param);

            } else if (filePolicy.getFilePolicyType().equals(FilePolicyType.file_snapshot.name())) {
                updateFileSnapshotPolicy(filePolicy, param);
            }

            String task = UUID.randomUUID().toString();
            Operation op = _dbClient.createTaskOpStatus(FilePolicy.class, filePolicy.getId(),
                    task, ResourceOperationTypeEnum.UPDATE_FILE_POLICY_BY_POLICY_STORAGE_RESOURCE);
            op.setDescription("update file protection policy by policy storage resource");
            return toTask(filePolicy, task, op);
        }
    }

    /**
     * @brief Get the list of policy storage resources of a file policy.
     * 
     * @param id of the file policy.
     * @return List of policy storage resource information.
     */
    @GET
    @Path("/{id}/policy-storage-resources")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public FilePolicyStorageResources getFilePolicyStorageResources(@PathParam("id") URI id) {

        _log.info("Request recieved to list of storage resource for the policy {}", id);
        FilePolicy filepolicy = queryResource(id);
        ArgValidator.checkEntity(filepolicy, id, true);
        FilePolicyStorageResources resources = new FilePolicyStorageResources();

        List<FilePolicyStorageResourceRestRep> policyResources = new ArrayList<FilePolicyStorageResourceRestRep>();
        for (PolicyStorageResource storageRes : FileOrchestrationUtils.getFilePolicyStorageResources(_dbClient, filepolicy)) {
            policyResources.add(FilePolicyMapper.mapPolicyStorageResource(storageRes, filepolicy, _dbClient));
        }
        resources.setStorageResources(policyResources);
        return resources;
    }

    /**
     * @brief Get the list of policy storage resources of a file policy.
     * 
     * @param id of the file policy.
     * @return List of policy storage resource information.
     */
    @GET
    @Path("/{id}/policy-storage-resources/{resId}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public FilePolicyStorageResourceRestRep getFilePolicyStorageResource(@PathParam("id") URI id, @PathParam("resId") URI resId) {

        _log.info("Request recieved to list of storage resource for the policy {}", id);
        FilePolicy filepolicy = queryResource(id);
        ArgValidator.checkEntity(filepolicy, id, true);

        ArgValidator.checkUri(resId);
        PolicyStorageResource storageRes = _dbClient.queryObject(PolicyStorageResource.class, resId);
        return FilePolicyMapper.mapPolicyStorageResource(storageRes, filepolicy, _dbClient);
    }

    /**
     * @brief Get the list of policy storage resources of a file policy.
     * 
     * @param id of the file policy.
     * @return List of policy storage resource information.
     */
    @GET
    @Path("/{id}/policy-storage-resources/{resId}/policy")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public FilePolicyRestRep getFileProtectionPolicyFromPolicyStorageResource(@PathParam("id") URI id, @PathParam("resId") URI resId) {

        _log.info("Request recieved to list of storage resource for the policy {}", id);
        FilePolicy filepolicy = queryResource(id);
        ArgValidator.checkEntity(filepolicy, id, true);

        ArgValidator.checkUri(resId);
        PolicyStorageResource storageRes = _dbClient.queryObject(PolicyStorageResource.class, resId);
        if (filepolicy != null && storageRes != null
                && storageRes.getFilePolicyId().toString().equalsIgnoreCase(filepolicy.getId().toString())) {
            return getFileProtectionPolicyFromPolicyStorageResource(filepolicy, storageRes);
        } else {
            String errorMsg = "Provided storage resource URI " + resId + " is not belongs to file policy:" + filepolicy.getId()
                    + " or it is a invalid URI";
            _log.error(errorMsg.toString());
            throw APIException.badRequests.invalidPolicyResourceParam(errorMsg.toString());
        }
    }

    /**
     * Validate and create replication policy.
     * 
     * @param param
     * @return
     */
    private FilePolicyCreateResp createFileReplicationPolicy(FilePolicyCreateParam param) {
        StringBuilder errorMsg = new StringBuilder();
        FilePolicy fileReplicationPolicy = new FilePolicy();

        if (param.getReplicationPolicyParams() == null) {
            errorMsg.append("Required parameter replication_params was missing or empty");
            _log.error("Failed to create snapshot policy due to {} ", errorMsg.toString());
            throw APIException.badRequests.invalidFileReplicationPolicyParam(param.getPolicyName(), errorMsg.toString());
        }

        // Validate replication policy schedule parameters
        boolean isValidSchedule = FilePolicyServiceUtils.validateAndUpdatePolicySchdeuleParam(
                param.getReplicationPolicyParams().getPolicySchedule(), fileReplicationPolicy, errorMsg);
        if (!isValidSchedule && errorMsg.length() > 0) {
            _log.error("Failed to create file replication policy due to {} ", errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyScheduleParam(param.getPolicyName(), errorMsg.toString());
        }
        // validate replication type and copy mode parameters
        ArgValidator.checkFieldValueFromEnum(param.getReplicationPolicyParams().getReplicationType(), "replicationType",
                EnumSet.allOf(FilePolicy.FileReplicationType.class));

        ArgValidator.checkFieldValueFromEnum(param.getReplicationPolicyParams().getReplicationCopyMode(), "replicationCopyMode",
                EnumSet.allOf(FilePolicy.FileReplicationCopyMode.class));

        fileReplicationPolicy.setId(URIUtil.createId(FilePolicy.class));
        fileReplicationPolicy.setFilePolicyName(param.getPolicyName());
        fileReplicationPolicy.setFilePolicyType(param.getPolicyType());
        fileReplicationPolicy.setPriority(param.getPriority());
        fileReplicationPolicy.setNumWorkerThreads((long) param.getNumWorkerThreads());
        if (param.getPolicyDescription() != null && !param.getPolicyDescription().isEmpty()) {
            fileReplicationPolicy.setFilePolicyDescription(param.getPolicyDescription());
        }
        fileReplicationPolicy.setScheduleFrequency(param.getReplicationPolicyParams().getPolicySchedule().getScheduleFrequency());
        fileReplicationPolicy.setFileReplicationType(param.getReplicationPolicyParams().getReplicationType());
        fileReplicationPolicy.setFileReplicationCopyMode(param.getReplicationPolicyParams().getReplicationCopyMode());
        fileReplicationPolicy.setApplyAt(param.getApplyAt());
        _dbClient.createObject(fileReplicationPolicy);
        _log.info("Policy {} created successfully", fileReplicationPolicy);

        return new FilePolicyCreateResp(fileReplicationPolicy.getId(), toLink(ResourceTypeEnum.FILE_POLICY,
                fileReplicationPolicy.getId()), fileReplicationPolicy.getLabel());
    }

    /**
     * Validate and create snapshot policy.
     * 
     * @param param
     * @return
     */
    private FilePolicyCreateResp createFileSnapshotPolicy(FilePolicyCreateParam param) {
        StringBuilder errorMsg = new StringBuilder();
        FilePolicy fileSnapshotPolicy = new FilePolicy();

        if (param.getSnapshotPolicyPrams() == null) {
            errorMsg.append("Required parameter snapshot_params was missing or empty");
            _log.error("Failed to create snapshot policy due to {} ", errorMsg.toString());
            throw APIException.badRequests.invalidFileSnapshotPolicyParam(param.getPolicyName(), errorMsg.toString());
        }

        // Validate snapshot policy schedule parameters
        boolean isValidSchedule = FilePolicyServiceUtils.validateAndUpdatePolicySchdeuleParam(
                param.getSnapshotPolicyPrams().getPolicySchedule(), fileSnapshotPolicy, errorMsg);
        if (!isValidSchedule && errorMsg.length() > 0) {
            _log.error("Failed to create file snapshot policy due to {} ", errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyScheduleParam(param.getPolicyName(), errorMsg.toString());
        }

        // Validate snapshot policy expire parameters..
        FilePolicyServiceUtils.validateSnapshotPolicyExpireParam(param.getSnapshotPolicyPrams());

        fileSnapshotPolicy.setId(URIUtil.createId(FilePolicy.class));
        fileSnapshotPolicy.setLabel(param.getPolicyName());
        fileSnapshotPolicy.setFilePolicyType(param.getPolicyType());
        fileSnapshotPolicy.setFilePolicyName(param.getPolicyName());
        if (param.getPolicyDescription() != null && !param.getPolicyDescription().isEmpty()) {
            fileSnapshotPolicy.setFilePolicyDescription(param.getPolicyDescription());
        }
        fileSnapshotPolicy.setScheduleFrequency(param.getSnapshotPolicyPrams().getPolicySchedule().getScheduleFrequency());
        fileSnapshotPolicy.setSnapshotExpireType(param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireType());
        fileSnapshotPolicy.setSnapshotNamePattern(param.getSnapshotPolicyPrams().getSnapshotNamePattern());
        fileSnapshotPolicy.setApplyAt(param.getApplyAt());
        if (!param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireType()
                .equalsIgnoreCase(SnapshotExpireType.NEVER.toString())) {
            fileSnapshotPolicy.setSnapshotExpireTime((long) param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireValue());
        }
        _dbClient.createObject(fileSnapshotPolicy);
        _log.info("Snapshot policy {} created successfully", fileSnapshotPolicy);

        return new FilePolicyCreateResp(fileSnapshotPolicy.getId(), toLink(ResourceTypeEnum.FILE_POLICY,
                fileSnapshotPolicy.getId()), fileSnapshotPolicy.getLabel());
    }

    /**
     * Update the replication policy.
     * 
     * @param fileReplicationPolicy
     * @param param
     * @return
     */
    private void updateFileReplicationPolicy(FilePolicy fileReplicationPolicy, FilePolicyUpdateParam param) {
        StringBuilder errorMsg = new StringBuilder();
        // validate and update common parameters!!
        updatePolicyCommonParameters(fileReplicationPolicy, param);

        if (param.getPriority() != null) {
            fileReplicationPolicy.setPriority(param.getPriority());
        }

        if (param.getNumWorkerThreads() > 0) {
            fileReplicationPolicy.setNumWorkerThreads((long) param.getNumWorkerThreads());
        }

        // validate and update replication parameters!!!
        if (param.getReplicationPolicyParams() != null) {
            FileReplicationPolicyParam replParam = param.getReplicationPolicyParams();

            if (replParam.getReplicationCopyMode() != null && !replParam.getReplicationCopyMode().isEmpty()) {
                ArgValidator.checkFieldValueFromEnum(param.getReplicationPolicyParams().getReplicationCopyMode(), "replicationCopyMode",
                        EnumSet.allOf(FilePolicy.FileReplicationCopyMode.class));
                fileReplicationPolicy.setFileReplicationCopyMode(replParam.getReplicationCopyMode());
            }

            if (replParam.getReplicationType() != null && !replParam.getReplicationType().isEmpty()) {
                ArgValidator.checkFieldValueFromEnum(replParam.getReplicationType(), "replicationType",
                        EnumSet.allOf(FilePolicy.FileReplicationType.class));
                // <TODO>
                // Dont change the replication type, if policy was applied to storage resources!!

                fileReplicationPolicy.setFileReplicationType(replParam.getReplicationType());
            }

            // Validate replication policy schedule parameters
            if (replParam.getPolicySchedule() != null) {
                boolean isValidSchedule = FilePolicyServiceUtils.validateAndUpdatePolicySchdeuleParam(
                        replParam.getPolicySchedule(), fileReplicationPolicy, errorMsg);
                if (!isValidSchedule && errorMsg.length() > 0) {
                    _log.error("Failed to update file replication policy due to {} ", errorMsg.toString());
                    throw APIException.badRequests.invalidFilePolicyScheduleParam(fileReplicationPolicy.getFilePolicyName(),
                            errorMsg.toString());
                }
                if (replParam.getPolicySchedule().getScheduleFrequency() != null &&
                        !replParam.getPolicySchedule().getScheduleFrequency().isEmpty()) {
                    fileReplicationPolicy
                            .setScheduleFrequency(replParam.getPolicySchedule().getScheduleFrequency());
                }
            }
        }
    }

    /**
     * validate policy parameters.
     * 
     * @param policy
     * @param param
     * @return
     */
    private void validateFilePolicyUpdateParams(FilePolicy policy, FilePolicyUpdateParam param) {
        StringBuilder errorMsg = new StringBuilder();

        // Verify the policy name!!!
        if (param.getPolicyName() != null && !param.getPolicyName().isEmpty()
                && !policy.getLabel().equalsIgnoreCase(param.getPolicyName())) {
            checkForDuplicateName(param.getPolicyName(), FilePolicy.class);
        }

        if (param.getPriority() != null) {
            ArgValidator.checkFieldValueFromEnum(param.getPriority(), "priority",
                    EnumSet.allOf(FilePolicy.FilePolicyPriority.class));
        }

        if (FilePolicyType.file_replication.name().equalsIgnoreCase(policy.getFilePolicyType())) {
            // validate replication parameters!!!
            if (param.getReplicationPolicyParams() != null) {
                FileReplicationPolicyParam replParam = param.getReplicationPolicyParams();

                if (replParam.getReplicationCopyMode() != null && !replParam.getReplicationCopyMode().isEmpty()) {
                    ArgValidator.checkFieldValueFromEnum(param.getReplicationPolicyParams().getReplicationCopyMode(), "replicationCopyMode",
                            EnumSet.allOf(FilePolicy.FileReplicationCopyMode.class));
                }

                if (replParam.getReplicationType() != null && !replParam.getReplicationType().isEmpty()) {
                    ArgValidator.checkFieldValueFromEnum(replParam.getReplicationType(), "replicationType",
                            EnumSet.allOf(FilePolicy.FileReplicationType.class));
                }

                // Validate replication policy schedule parameters
                if (replParam.getPolicySchedule() != null) {
                    boolean isValidSchedule = FilePolicyServiceUtils.validatePolicySchdeuleParam(
                            replParam.getPolicySchedule(), policy, errorMsg);
                    if (!isValidSchedule && errorMsg.length() > 0) {
                        _log.error("Invalid policy paramters: {} ", errorMsg.toString());
                        throw APIException.badRequests.invalidFilePolicyScheduleParam(policy.getFilePolicyName(),
                                errorMsg.toString());
                    }
                }
            }
        } else if (FilePolicyType.file_snapshot.name().equalsIgnoreCase(policy.getFilePolicyType())) {

            if (param.getSnapshotPolicyPrams() != null) {
                // Validate snapshot policy schedule parameters
                if (param.getSnapshotPolicyPrams().getPolicySchedule() != null) {
                    boolean isValidSchedule = FilePolicyServiceUtils.validatePolicySchdeuleParam(
                            param.getSnapshotPolicyPrams().getPolicySchedule(), policy, errorMsg);
                    if (!isValidSchedule && errorMsg.length() > 0) {
                        _log.error("Failed to update file replication policy due to {} ", errorMsg.toString());
                        throw APIException.badRequests.invalidFilePolicyScheduleParam(policy.getFilePolicyName(),
                                errorMsg.toString());
                    }
                }

                // Validate snapshot policy expire parameters..
                if (param.getSnapshotPolicyPrams().getSnapshotExpireParams() != null) {
                    FilePolicyServiceUtils.validateSnapshotPolicyExpireParam(param.getSnapshotPolicyPrams());
                }
            }
        }
    }

    /**
     * Update snapshot policy.
     * 
     * @param param
     * @param fileSnapshotPolicy
     * @return
     */
    private void updateFileSnapshotPolicy(FilePolicy fileSnapshotPolicy, FilePolicyUpdateParam param) {
        StringBuilder errorMsg = new StringBuilder();
        // validate and update common parameters!!
        updatePolicyCommonParameters(fileSnapshotPolicy, param);

        // Validate snapshot policy expire parameters..
        if (param.getSnapshotPolicyPrams() != null) {

            // Validate snapshot policy schedule parameters
            if (param.getSnapshotPolicyPrams().getPolicySchedule() != null) {
                boolean isValidSchedule = FilePolicyServiceUtils.validateAndUpdatePolicySchdeuleParam(
                        param.getSnapshotPolicyPrams().getPolicySchedule(), fileSnapshotPolicy, errorMsg);
                if (!isValidSchedule && errorMsg.length() > 0) {
                    _log.error("Failed to update file replication policy due to {} ", errorMsg.toString());
                    throw APIException.badRequests.invalidFilePolicyScheduleParam(fileSnapshotPolicy.getFilePolicyName(),
                            errorMsg.toString());
                }
                if (param.getSnapshotPolicyPrams().getPolicySchedule().getScheduleFrequency() != null &&
                        !param.getSnapshotPolicyPrams().getPolicySchedule().getScheduleFrequency().isEmpty()) {
                    fileSnapshotPolicy.setScheduleFrequency(param.getSnapshotPolicyPrams().getPolicySchedule().getScheduleFrequency());
                }
            }

            if (param.getSnapshotPolicyPrams().getSnapshotExpireParams() != null) {
                // Validate the snapshot expire parameters!!
                FileSnapshotPolicyExpireParam snapExpireParam = param.getSnapshotPolicyPrams().getSnapshotExpireParams();
                FilePolicyServiceUtils.validateSnapshotPolicyExpireParam(param.getSnapshotPolicyPrams());
                if (snapExpireParam.getExpireType() != null) {
                    fileSnapshotPolicy.setSnapshotExpireType(snapExpireParam.getExpireType());
                    if (!SnapshotExpireType.NEVER.toString().equalsIgnoreCase(snapExpireParam.getExpireType())) {
                        fileSnapshotPolicy.setSnapshotExpireTime((long) snapExpireParam.getExpireValue());
                    }
                }
            }

            if (param.getSnapshotPolicyPrams().getSnapshotNamePattern() != null) {
                fileSnapshotPolicy.setSnapshotNamePattern(param.getSnapshotPolicyPrams().getSnapshotNamePattern());
            }
        }
    }

    private FilePolicyRestRep getFileProtectionPolicyFromPolicyStorageResource(FilePolicy policy, PolicyStorageResource policyRes) {

        FilePolicyRestRep storageSystemPolicyRep = new FilePolicyRestRep();
        int timeout = 100;
        // valid value of timeout is 10 sec to 10 min
        if (timeout < 10 || timeout > 600) {
            timeout = 30;// default timeout value.
        }

        String task = UUID.randomUUID().toString();
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, policyRes.getStorageSystem());
        FileController controller = getController(FileController.class, device.getSystemType());
        Operation op = _dbClient.createTaskOpStatus(FileShare.class, policyRes.getFilePolicyId(),
                task, ResourceOperationTypeEnum.GET_FILE_POLICY_BY_POLICY_STORAGE_RESOURCE);
        op.setDescription("file protection policy by policy storage resource");

        try {

            _log.info("Getting file protection policy on storage system for file storage resource {}, {}", device.getLabel(),
                    policyRes.getResourcePath());

            controller.getFileProtectionPolicyFromStorageSystem(device.getId(), policy.getId(), policyRes.getId(), task);
            Task taskObject = null;
            auditOp(OperationTypeEnum.GET_STORAGE_SYSTEM_POLICY_BY_POLICY_RESOURCE, true, AuditLogManager.AUDITOP_BEGIN,
                    policy.getId().toString(), device.getId().toString(), policyRes.getId());
            int timeoutCounter = 0;
            // wait till timeout or result from controller service ,whichever is earlier
            do {
                TimeUnit.SECONDS.sleep(1);
                taskObject = TaskUtils.findTaskForRequestId(_dbClient, policy.getId(), task);
                timeoutCounter++;
                // exit the loop if task is completed with error/success or timeout
            } while ((taskObject != null && !(taskObject.isReady() || taskObject.isError())) && timeoutCounter < timeout);

            if (taskObject == null) {
                throw APIException.badRequests
                        .unableToProcessRequest("Error occured while getting storage sytem policy for policy storage resouce");
            } else if (taskObject.isReady()) {
                FilePolicy storageSystemPolicy = getTaggedStorageSystemPolicyFromDB("StorageSystem Policy");
                storageSystemPolicyRep = map(storageSystemPolicy, _dbClient);
                // Mark the temporary policy as deleted!!!
                storageSystemPolicy.setInactive(true);
                _dbClient.updateObject(storageSystemPolicy);
            } else if (taskObject.isError()) {
                throw APIException.badRequests
                        .unableToProcessRequest("Error occured while getting storage sytem policy for policy storage resouce due to"
                                + taskObject.getMessage());
            } else {
                throw APIException.badRequests
                        .unableToProcessRequest(
                                "Error occured while getting storage sytem policy for policy storage resouce due to timeout");
            }

        } catch (BadRequestException e) {
            op = _dbClient.error(FilePolicy.class, policy.getId(), task, e);
            _log.error("Error while getting storage sytem policy for policy storage resouce {}, {}", e.getMessage(), e);
            throw APIException.badRequests.unableToProcessRequest(e.getMessage());
        } catch (Exception e) {
            _log.error("Error while getting storage sytem policy for policy storage resouce {}, {}", e.getMessage(), e);
            throw APIException.badRequests.unableToProcessRequest(e.getMessage());
        }
        return storageSystemPolicyRep;

    }

    private TaskResourceRep updateStorageSystemFileProtectionPolicy(FilePolicy policy, FilePolicyUpdateParam param) {

        String task = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(FilePolicy.class, policy.getId(),
                task, ResourceOperationTypeEnum.UPDATE_FILE_POLICY_BY_POLICY_STORAGE_RESOURCE);
        op.setDescription("update file protection policy by policy storage resource");

        try {
            FileServiceApi fileServiceApi = getDefaultFileServiceApi();
            fileServiceApi.updateFileProtectionPolicy(policy.getId(), param, task);
            _log.info("Updated file protection policy {}", policy.getFilePolicyName());
        } catch (BadRequestException e) {
            op = _dbClient.error(FilePolicy.class, policy.getId(), task, e);
            _log.error("Error Unassigning File policy {}, {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            _log.error("Error Unassigning Files Policy {}, {}", e.getMessage(), e);
            throw APIException.badRequests.unableToProcessRequest(e.getMessage());
        }
        return toTask(policy, task, op);

    }

    private FilePolicy getTaggedStorageSystemPolicyFromDB(String tagName) {
        SearchedResRepList resRepList = null;
        if (tagName != null && !tagName.isEmpty()) {
            resRepList = new SearchedResRepList(getResourceType());
            _dbClient.queryByConstraint(
                    PrefixConstraint.Factory.getTagsPrefixConstraint(getResourceClass(), tagName),
                    resRepList);
            for (SearchResultResourceRep resource : resRepList) {
                FilePolicy policy = _dbClient.queryObject(FilePolicy.class, resource.getId());
                if (policy != null && !policy.getInactive()) {
                    _log.info("Got Storage resouce file policy {}", policy.getFilePolicyName());
                    return policy;
                }

            }
        }
        _log.info("No Storage resouce file policy with tag {}", tagName);
        return null;
    }

    private boolean updatePolicyCommonParameters(FilePolicy existingPolicy, FilePolicyUpdateParam param) {

        // Verify and updated the policy name!!!
        if (param.getPolicyName() != null && !param.getPolicyName().isEmpty()
                && !existingPolicy.getLabel().equalsIgnoreCase(param.getPolicyName())) {
            checkForDuplicateName(param.getPolicyName(), FilePolicy.class);
            existingPolicy.setLabel(param.getPolicyName());
            existingPolicy.setFilePolicyName(param.getPolicyName());
        }

        if (param.getPolicyDescription() != null && !param.getPolicyDescription().isEmpty()) {
            existingPolicy.setFilePolicyDescription(param.getPolicyDescription());
        }
        return true;
    }

    private List<FileReplicationTopology> queryDBReplicationTopologies(FilePolicy policy) {
        _log.info("Querying all DB replication topologies Using policy Id {}", policy.getId());
        try {
            ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory
                    .getFileReplicationPolicyTopologyConstraint(policy.getId());
            List<FileReplicationTopology> topologies = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient,
                    FileReplicationTopology.class,
                    containmentConstraint);
            return topologies;
        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }

        return null;
    }

    private void updateFileReplicationTopologyInfo(FilePolicyAssignParam param, FilePolicy filepolicy) {

        if (FilePolicyType.file_replication.name().equalsIgnoreCase(filepolicy.getFilePolicyType())
                && filepolicy.getFileReplicationType().equalsIgnoreCase(FileReplicationType.REMOTE.name())) {
            if (param.getFileReplicationtopologies() != null && !param.getFileReplicationtopologies().isEmpty()) {
                List<FileReplicationTopology> dbTopologies = queryDBReplicationTopologies(filepolicy);
                for (FileReplicationTopologyParam topologyParam : param.getFileReplicationtopologies()) {
                    // Get existing topologies for given policy
                    Boolean foundExistingTopology = false;
                    if (dbTopologies != null && !dbTopologies.isEmpty()) {
                        for (FileReplicationTopology topology : dbTopologies) {
                            if (topology.getSourceVArray() != null
                                    && topology.getSourceVArray().toString().equalsIgnoreCase(topologyParam.getSourceVArray().toString())) {
                                _log.info("Updating the existing topology");
                                if (!topology.getTargetVArrays().containsAll(topology.getTargetVArrays())) {
                                    topology.addTargetVArrays(topology.getTargetVArrays());
                                    _dbClient.updateObject(topology);
                                }
                                if (filepolicy.getReplicationTopologies() == null
                                        || !filepolicy.getReplicationTopologies().contains(topology.getId().toString())) {
                                    filepolicy.addReplicationTopology(topology.getId().toString());
                                }
                                foundExistingTopology = true;
                                break;
                            }
                        }
                    }

                    if (!foundExistingTopology) {
                        // Create DB entry for Replication topology
                        FileReplicationTopology dbReplTopology = new FileReplicationTopology();
                        dbReplTopology.setId(URIUtil.createId(FileReplicationTopology.class));
                        dbReplTopology.setPolicy(filepolicy.getId());
                        dbReplTopology.setSourceVArray(topologyParam.getSourceVArray());
                        StringSet targetArrays = new StringSet();
                        if (topologyParam.getTargetVArrays() != null && !topologyParam.getTargetVArrays().isEmpty()) {
                            for (URI uriTargetArray : topologyParam.getTargetVArrays()) {
                                targetArrays.add(uriTargetArray.toString());
                            }
                            dbReplTopology.setTargetVArrays(targetArrays);
                        }
                        _dbClient.createObject(dbReplTopology);
                        if (filepolicy.getReplicationTopologies() == null
                                || !filepolicy.getReplicationTopologies().contains(dbReplTopology.getId().toString())) {
                            filepolicy.addReplicationTopology(dbReplTopology.getId().toString());
                        }
                    }
                }
            }
        }
    }

    /**
     * Assigning policy at vpool level
     * 
     * @param param
     * @param filepolicy
     */
    private TaskResourceRep assignFilePolicyToVpool(FilePolicyAssignParam param, FilePolicy filePolicy) {
        StringBuilder errorMsg = new StringBuilder();
        ArgValidator.checkFieldNotNull(param.getVpoolAssignParams(), "vpool_assign_param");

        // Policy has to be applied on specified file vpools..
        ArgValidator.checkFieldNotNull(param.getVpoolAssignParams().getAssigntoVpools(), "assign_to_vpools");
        Set<URI> vpoolURIs = param.getVpoolAssignParams().getAssigntoVpools();
        Map<URI, List<URI>> vpoolToStorageSystemMap = new HashMap<URI, List<URI>>();

        for (URI vpoolURI : vpoolURIs) {
            ArgValidator.checkFieldUriType(vpoolURI, VirtualPool.class, "vpool");
            VirtualPool virtualPool = _permissionsHelper.getObjectById(vpoolURI, VirtualPool.class);

            if (filePolicy.getAssignedResources() != null && filePolicy.getAssignedResources().contains(virtualPool.getId().toString())) {
                _log.info("File policy: {} has already been assigned to vpool: {} ", filePolicy.getFilePolicyName(),
                        virtualPool.getLabel());
                continue;
            }

            // Verify the vpool has any replication policy!!!
            // only single replication policy per vpool.
            if (FilePolicyServiceUtils.vPoolHasReplicationPolicy(_dbClient, vpoolURI)) {
                errorMsg.append("Provided vpool : " + virtualPool.getLabel() + " already assigned with replication policy.");
                _log.error(errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyAssignParam(filePolicy.getFilePolicyName(), errorMsg.toString());
            }
            // Verify user has permission to assign policy
            canUserAssignPolicyAtGivenLevel(filePolicy);
            ArgValidator.checkEntity(virtualPool, vpoolURI, false);

            FilePolicyServiceUtils.validateVpoolSupportPolicyType(filePolicy, virtualPool);
            vpoolToStorageSystemMap.put(vpoolURI, getAssociatedStorageSystemsByVPool(virtualPool));
        }

        if (param.getApplyOnTargetSite() != null) {
            filePolicy.setApplyOnTargetSite(param.getApplyOnTargetSite());
        }

        // update replication topology info
        updateFileReplicationTopologyInfo(param, filePolicy);
        String task = UUID.randomUUID().toString();
        TaskResourceRep taskObject = createAssignFilePolicyTask(filePolicy, task);
        FileServiceApi fileServiceApi = getDefaultFileServiceApi();
        AssignFileSnapshotPolicyToVpoolSchedulingThread.executeApiTask(this, _asyncTaskService.getExecutorService(), _dbClient,
                filePolicy.getId(), vpoolToStorageSystemMap, fileServiceApi, taskObject, task);

        auditOp(OperationTypeEnum.ASSIGN_FILE_POLICY, true, AuditLogManager.AUDITOP_BEGIN,
                filePolicy.getLabel());

        return taskObject;
    }

    /**
     * Assign policy at project level
     * 
     * @param param
     * @param filepolicy
     */
    private TaskResourceRep assignFilePolicyToProject(FilePolicyAssignParam param, FilePolicy filePolicy) {

        StringBuilder errorMsg = new StringBuilder();
        ArgValidator.checkFieldNotNull(param.getProjectAssignParams(), "project_assign_param");
        ArgValidator.checkFieldUriType(param.getProjectAssignParams().getVpool(), VirtualPool.class, "vpool");
        URI vpoolURI = param.getProjectAssignParams().getVpool();
        VirtualPool vpool = null;

        if (filePolicy.getFilePolicyVpool() == null) {
            ArgValidator.checkFieldUriType(vpoolURI, VirtualPool.class, "vpool");
            vpool = _permissionsHelper.getObjectById(vpoolURI, VirtualPool.class);
            ArgValidator.checkEntity(vpool, vpoolURI, false);

            // Check if the vpool supports provided policy type..
            FilePolicyServiceUtils.validateVpoolSupportPolicyType(filePolicy, vpool);

            // Check if the vpool supports policy at project level..
            if (!vpool.getAllowFilePolicyAtProjectLevel()) {
                errorMsg.append("Provided vpool :" + vpool.getLabel() + " doesn't support policy at project level");
                _log.error(errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyAssignParam(filePolicy.getFilePolicyName(), errorMsg.toString());
            }

        } else if (vpoolURI != null) {
            vpool = _dbClient.queryObject(VirtualPool.class, filePolicy.getFilePolicyVpool());
            if (!vpoolURI.equals(filePolicy.getFilePolicyVpool())) {
                errorMsg.append("File policy: " + filePolicy.getFilePolicyName()
                        + " is already assigned at project level under the vpool: "
                        + vpool.getLabel());
                _log.error(errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyAssignParam(filePolicy.getFilePolicyName(), errorMsg.toString());
            }
        }

        ArgValidator.checkFieldNotNull(param.getProjectAssignParams().getAssigntoProjects(), "assign_to_projects");
        Set<URI> projectURIs = param.getProjectAssignParams().getAssigntoProjects();

        for (URI projectURI : projectURIs) {
            ArgValidator.checkFieldUriType(projectURI, Project.class, "project");
            Project project = _permissionsHelper.getObjectById(projectURI, Project.class);
            ArgValidator.checkEntity(project, projectURI, false);

            if (filePolicy.getAssignedResources() != null && filePolicy.getAssignedResources().contains(project.getId().toString())) {
                _log.info("Policy {} is already assigned to project {} ", filePolicy.getFilePolicyName(), project.getLabel());
                continue;
            }

            // Verify the vpool - project has any replication policy!!!
            // only single replication policy per vpool-project combination.
            if (FilePolicyServiceUtils.projectHasReplicationPolicy(_dbClient, projectURI, filePolicy.getFilePolicyVpool())) {
                errorMsg.append("Virtual pool " + filePolicy.getFilePolicyVpool().toString() + " project " + project.getLabel()
                        + "pair is already assigned with replication policy.");
                _log.error(errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyAssignParam(filePolicy.getFilePolicyName(), errorMsg.toString());
            }
            // Verify user has permission to assign policy
            canUserAssignPolicyAtGivenLevel(filePolicy);
        }

        // update replication topology info
        updateFileReplicationTopologyInfo(param, filePolicy);
        Map<URI, List<URI>> vpoolToStorageSystemMap = new HashMap<URI, List<URI>>();
        vpoolToStorageSystemMap.put(vpoolURI, getAssociatedStorageSystemsByVPool(vpool));

        if (param.getApplyOnTargetSite() != null) {
            filePolicy.setApplyOnTargetSite(param.getApplyOnTargetSite());
        }

        String task = UUID.randomUUID().toString();
        TaskResourceRep taskObject = createAssignFilePolicyTask(filePolicy, task);
        FileServiceApi fileServiceApi = getDefaultFileServiceApi();
        AssignFileSnapshotPolicyToProjectSchedulingThread.executeApiTask(this, _asyncTaskService.getExecutorService(), _dbClient,
                filePolicy.getId(), vpoolToStorageSystemMap, new ArrayList<URI>(projectURIs), fileServiceApi, taskObject, task);

        auditOp(OperationTypeEnum.ASSIGN_FILE_POLICY, true, AuditLogManager.AUDITOP_BEGIN,
                filePolicy.getLabel());

        return taskObject;
    }

    private TaskResourceRep assignFilePolicyToFS(FilePolicyAssignParam param, FilePolicy filepolicy) {
        StringBuilder errorMsg = new StringBuilder();

        ArgValidator.checkFieldNotNull(param.getFileSystemAssignParams(), "filesystem_assign_param");

        if (filepolicy.getFilePolicyVpool() == null) {
            ArgValidator.checkFieldNotNull(param.getFileSystemAssignParams().getVpool(), "vpool");
            ArgValidator.checkFieldUriType(param.getFileSystemAssignParams().getVpool(), VirtualPool.class, "vpool");
            VirtualPool vpool = _permissionsHelper.getObjectById(param.getFileSystemAssignParams().getVpool(), VirtualPool.class);
            ArgValidator.checkEntity(vpool, param.getFileSystemAssignParams().getVpool(), false);

            // Check if the vpool supports provided policy type..
            FilePolicyServiceUtils.validateVpoolSupportPolicyType(filepolicy, vpool);

            // Check if the vpool supports policy at file system level..
            if (!vpool.getAllowFilePolicyAtFSLevel()) {
                errorMsg.append("Provided vpool :" + vpool.getLabel() + " doesn't support policy at file system level");
                _log.error(errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
            }
            filepolicy.setFilePolicyVpool(param.getFileSystemAssignParams().getVpool());
            // Check if the vpool supports policy at file system level..
            if (!vpool.getAllowFilePolicyAtFSLevel()) {
                errorMsg.append("Provided vpool :" + vpool.getId().toString() + " doesn't support policy at file system level");
            } else if (param.getFileSystemAssignParams().getVpool() != null
                    && !param.getFileSystemAssignParams().getVpool().equals(filepolicy.getFilePolicyVpool())) {
                errorMsg.append(
                        "File policy :" + filepolicy.getFilePolicyName() + " is already assigned at file system level under the vpool: "
                                + filepolicy.getFilePolicyVpool());
                _log.error(errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
            }
            if (param.getApplyOnTargetSite() != null) {
                filepolicy.setApplyOnTargetSite(param.getApplyOnTargetSite());
            }
        }
        // update replication topology info
        updateFileReplicationTopologyInfo(param, filepolicy);
        if ((param.getFileSystemAssignParams().getVpool() != null
                && !param.getFileSystemAssignParams().getVpool().equals(filepolicy.getFilePolicyVpool()))) {
            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, filepolicy.getFilePolicyVpool());
            errorMsg.append("File policy :" + filepolicy.getFilePolicyName()
                    + " is already assigned at file system level under the vpool: "
                    + vpool.getLabel());
            _log.error(errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
        }
        this._dbClient.updateObject(filepolicy);
        // TODO Remove this and create a proper resource
        return new TaskResourceRep();
    }

    private void canUserUnAssignPolicyAtGivenLevel(FilePolicy policy, URI res) {
        FilePolicyApplyLevel applyLevel = FilePolicyApplyLevel.valueOf(policy.getApplyAt());
        switch (applyLevel) {
            case vpool:
                if (!userHasSystemAdminRoles()) {
                    _log.error("User does not sufficient roles to unassign policy at vpool");
                    throw APIException.forbidden.onlySystemAdminsCanAssignVpoolPolicies(policy.getFilePolicyName());
                } else {
                    _permissionsHelper.getObjectById(res, VirtualPool.class);
                }
                break;
            case project:
                if (!userHasTenantAdminRoles()) {
                    _log.error("User does not sufficient roles to unassign policy at project");
                    throw APIException.forbidden.onlyTenantAdminsCanAssignProjectPolicies(policy.getFilePolicyName());
                } else {
                    _permissionsHelper.getObjectById(res, Project.class);
                }
                break;
            case file_system:
                if (!userHasTenantAdminRoles()) {
                    _log.error("User does not sufficient roles to unassign policy at file system");
                    throw APIException.forbidden.onlyTenantAdminsCanAssignFileSystemPolicies(policy.getFilePolicyName());
                }
                break;
            default:
                _log.error("Not a valid policy apply level: " + applyLevel);
        }
    }

    private boolean canUserAssignPolicyAtGivenLevel(FilePolicy policy) {

        // user should have system admin role to assign policy to vpool
        if (policy.getApplyAt() != null) {
            switch (policy.getApplyAt()) {
                case "vpool":
                    if (!userHasSystemAdminRoles()) {
                        _log.error("User does not sufficient roles to assign policy at vpool");
                        throw APIException.forbidden.onlySystemAdminsCanAssignVpoolPolicies(policy.getFilePolicyName());
                    }
                    break;
                case "project":
                    if (!userHasTenantAdminRoles()) {
                        _log.error("User does not sufficient roles to assign policy at project");
                        throw APIException.forbidden.onlyTenantAdminsCanAssignProjectPolicies(policy.getFilePolicyName());
                    }
                    // Verify the user has an access to given projects
                    break;
                case "file_system":
                    if (!userHasTenantAdminRoles()) {
                        _log.error("User does not sufficient roles to assign policy at file system");
                        throw APIException.forbidden.onlyTenantAdminsCanAssignFileSystemPolicies(policy.getFilePolicyName());
                    }
                    break;
                default:
                    return false;
            }
            return true;
        }

        return false;
    }

    private boolean canAccessFilePolicy(FilePolicy filePolicy) {
        StringSet tenants = filePolicy.getTenantOrg();

        if (tenants == null || tenants.isEmpty()) {
            return true;
        }

        if (isSystemAdmin()) {
            return true;
        }

        StorageOSUser user = getUserFromContext();
        String userTenantId = user.getTenantId();

        return tenants.contains(userTenantId);

    }

    private boolean doesResHasPolicyOfSameType(StringSet existingPolicies, FilePolicy filePolicy) {
        if (existingPolicies != null && !existingPolicies.isEmpty()) {
            List<URI> existingFilePolicyURIs = new ArrayList<URI>();
            for (String existingPolicy : existingPolicies) {
                existingFilePolicyURIs.add(URI.create(existingPolicy));
            }
            Iterator<FilePolicy> iterator = _dbClient.queryIterativeObjects(FilePolicy.class, existingFilePolicyURIs, true);

            if (filePolicy.getFilePolicyType().equals(FilePolicyType.file_replication.name())) {
                while (iterator.hasNext()) {
                    FilePolicy fp = iterator.next();
                    if (FilePolicy.FilePolicyType.file_replication.name().equals(fp.getFilePolicyType())) {
                        return true;
                    }
                }

            } else if (filePolicy.getFilePolicyType().equals(FilePolicyType.file_snapshot.name())) {
                while (iterator.hasNext()) {
                    FilePolicy fp = iterator.next();
                    if (FilePolicy.FilePolicyType.file_snapshot.name().equals(fp.getFilePolicyType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<URI> getAssociatedStorageSystemsByVPool(VirtualPool vpool) {

        Set<URI> storageSystemURISet = new HashSet<URI>();

        StringSet storagePoolURISet = null;
        if (vpool.getUseMatchedPools()) {
            storagePoolURISet = vpool.getMatchedStoragePools();
        } else {
            storagePoolURISet = vpool.getAssignedStoragePools();
        }

        if (storagePoolURISet != null && !storagePoolURISet.isEmpty()) {
            for (Iterator<String> iterator = storagePoolURISet.iterator(); iterator.hasNext();) {
                URI storagePoolURI = URI.create(iterator.next());
                StoragePool spool = _dbClient.queryObject(StoragePool.class, storagePoolURI);
                if (spool != null && !spool.getInactive()) {
                    storageSystemURISet.add(spool
                            .getStorageDevice());
                }
            }
        }
        return new ArrayList<URI>(storageSystemURISet);
    }

    private TaskResourceRep createAssignFilePolicyTask(FilePolicy filepolicy, String taskId) {
        filepolicy.setOpStatus(new OpStatusMap());
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.ASSIGN_FILE_POLICY);
        filepolicy.getOpStatus().createTaskStatus(taskId, op);
        _dbClient.updateObject(filepolicy);
        return toTask(filepolicy, taskId);
    }
}

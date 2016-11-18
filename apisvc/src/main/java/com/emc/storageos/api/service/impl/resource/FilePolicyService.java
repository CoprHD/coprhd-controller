/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.impl.resource.utils.FilePolicyServiceUtils;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FilePolicy.SnapshotExpireType;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.file.FilePolicyAssignParam;
import com.emc.storageos.model.file.FilePolicyParam;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;

/**
 * @author jainm15
 */
@Path("/file/filePolicies")
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.TENANT_ADMIN })
public class FilePolicyService extends TaskResourceService {
    private static final Logger _log = LoggerFactory.getLogger(FilePolicyService.class);

    protected static final String EVENT_SERVICE_SOURCE = "FilePolicyService";

    private static final String EVENT_SERVICE_TYPE = "FilePolicy";

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    private NetworkService networkSvc;

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Override
    protected DataObject queryResource(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        // TODO Auto-generated method stub
        return null;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    public FilePolicyParam createFilePolicy(FilePolicyParam param) {

        // Make policy name as mandatory field
        ArgValidator.checkFieldNotNull(param.getPolicyName(), "policyName");

        // Check for duplicate policy name
        if (param.getPolicyName() != null && !param.getPolicyName().isEmpty()) {
            checkForDuplicateName(param.getPolicyName(), FilePolicy.class);
        }
        // check policy type is valid or not
        ArgValidator.checkFieldValueFromEnum(param.getPolicyType(), "policy_type",
                EnumSet.allOf(FilePolicyType.class));

        _log.info("file policy creation started -- ");

        StringBuilder errorMsg = new StringBuilder();

        /**
         * Replication Policy Checks and creation.
         */
        if (param.getPolicyType().equals(FilePolicyType.file_replication.name())) {
            FilePolicy fileReplicationPolicy = new FilePolicy();

            // Validate replication policy schedule parameters
            boolean isValidSchedule = FilePolicyServiceUtils.validatePolicySchdeuleParam(
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
            fileReplicationPolicy.setLabel(param.getPolicyName());
            fileReplicationPolicy.setFilePolicyName(param.getPolicyName());
            fileReplicationPolicy.setFilePolicyType(param.getPolicyType());
            fileReplicationPolicy.setScheduleFrequency(param.getReplicationPolicyParams().getPolicySchedule().getScheduleFrequency());
            fileReplicationPolicy.setFileReplicationType(param.getReplicationPolicyParams().getReplicationType());
            fileReplicationPolicy.setFileReplicationCopyMode(param.getReplicationPolicyParams().getReplicationCopyMode());
            this._dbClient.createObject(fileReplicationPolicy);
            _log.info("Policy {} created successfully", fileReplicationPolicy);
        }

        /**
         * Snapshot Policy checks and creation.
         */
        else if (param.getPolicyType().equals(FilePolicyType.file_snapshot.name())) {
            FilePolicy fileSnapshotPolicy = new FilePolicy();

            // Validate snapshot policy schedule parameters
            boolean isValidSchedule = FilePolicyServiceUtils.validatePolicySchdeuleParam(
                    param.getSnapshotPolicyPrams().getPolicySchedule(), fileSnapshotPolicy, errorMsg);
            if (!isValidSchedule && errorMsg.length() > 0) {
                _log.error("Failed to create file snapshot policy due to {} ", errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyScheduleParam(param.getPolicyName(), errorMsg.toString());
            }

            // Validate snapshot policy expire parameters..
            if (param.getSnapshotPolicyPrams().getSnapshotExpireParams() != null) {
                FilePolicyServiceUtils.validateSnapshotPolicyParam(param.getSnapshotPolicyPrams());
            } else {
                errorMsg.append("Required parameter snapshot_expire was missing or empty");
                _log.error("Failed to create snapshot policy due to {} ", errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyScheduleParam(param.getPolicyName(), errorMsg.toString());
            }
            fileSnapshotPolicy.setId(URIUtil.createId(FilePolicy.class));
            fileSnapshotPolicy.setLabel(param.getPolicyName());
            fileSnapshotPolicy.setFilePolicyType(param.getPolicyType());
            fileSnapshotPolicy.setFilePolicyName(param.getPolicyName());
            fileSnapshotPolicy.setScheduleFrequency(param.getSnapshotPolicyPrams().getPolicySchedule().getScheduleFrequency());
            fileSnapshotPolicy.setSnapshotExpireType(param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireType());
            if (!param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireType()
                    .equalsIgnoreCase(SnapshotExpireType.NEVER.toString())) {
                fileSnapshotPolicy.setSnapshotExpireTime((long) param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireValue());
            }
            this._dbClient.createObject(fileSnapshotPolicy);
            _log.info("Snapshot policy {} created successfully", fileSnapshotPolicy);
        }

        /**
         * Quota Policy Creation and params checks.
         */
        else if (param.getPolicyType().equals(FilePolicyType.file_quota.name())) {
            //
        }
        return param;
    }

    @PUT
    @Path("/{id}/assign-policy")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    public void assignFilePolicy(@PathParam("id") URI id, FilePolicyAssignParam param) {

        ArgValidator.checkFieldUriType(id, FilePolicy.class, "id");
        FilePolicy filepolicy = this._dbClient.queryObject(FilePolicy.class, id);
        ArgValidator.checkEntity(filepolicy, id, true);

        ArgValidator.checkFieldValueFromEnum(param.getApplyAt(), "apply_at",
                EnumSet.allOf(FilePolicyApplyLevel.class));

        /**
         * Assigning policy at vpool level
         */
        if (param.getApplyAt().equals(FilePolicyApplyLevel.vpool.name())) {
            // Policy has to be applied on all applicable file vpools..
            ArgValidator.checkFieldNotNull(param.getVpoolAssignParams(), "assign_vpools");
            if (param.getVpoolAssignParams().isAssigntoAll()) {
                // policy has to be applied on all applicable file vpools
                List<URI> vpoolIDs = this._dbClient.queryByType(VirtualPool.class, true);
                List<VirtualPool> virtualPools = this._dbClient.queryObject(VirtualPool.class, vpoolIDs);
                Set<String> assignedResources = new HashSet<String>();
                for (VirtualPool virtualPool : virtualPools) {
                    if (virtualPool.getType().equals(VirtualPool.Type.file.name())
                            && virtualPool.getSupportedFileProtection().contains(filepolicy.getFilePolicyType())) {
                        assignedResources.add(virtualPool.getId().toString());
                    }
                }
                filepolicy.setApplyAt(FilePolicyApplyLevel.vpool.name());
                filepolicy.setAssignedResources(assignedResources);

            }// Policy has to be applied on specified file vpools..
            else {
                Set<String> inputVpools = param.getVpoolAssignParams().getAssigntoVpools();
                List<URI> vpoolIDs = this._dbClient.queryByType(VirtualPool.class, true);
                List<VirtualPool> virtualPools = this._dbClient.queryObject(VirtualPool.class, vpoolIDs);
                Set<String> assignedResources = new HashSet<String>();
                for (VirtualPool virtualPool : virtualPools) {
                    if (inputVpools.contains(virtualPool.getLabel())) {
                        if (virtualPool.getSupportedFileProtection().contains(filepolicy.getFilePolicyType())) {
                            assignedResources.add(virtualPool.getId().toString());
                        } else {
                            _log.error("Provided vpool {} doesn't support policy type: {} ",
                                    virtualPool.getLabel(), filepolicy.getFilePolicyType());
                            // throw exception, Since input contains vpool which doesn't support this type of policy
                        }
                    }
                }
                filepolicy.setApplyAt(FilePolicyApplyLevel.vpool.name());
                filepolicy.setAssignedResources(assignedResources);
            }
        }
        /**
         * Assign policy at project level
         */
        else if (param.getApplyAt().equals(FilePolicyApplyLevel.project.name())) {
            // Check vpool id provided by user
            ArgValidator.checkFieldUriType(param.getProjectAssignParams().getVpool(), VirtualPool.class, "vpool");
            VirtualPool vpool = this._dbClient.queryObject(VirtualPool.class, param.getProjectAssignParams().getVpool());
            ArgValidator.checkEntity(vpool, vpool.getId(), false);

            // Check if the vpool supports provided policy type
            if (!vpool.getSupportedFileProtection().contains(filepolicy.getFilePolicyType())) {
                // throw exception
            }

        }

        else if (param.getApplyAt().equals(FilePolicyApplyLevel.file_system.name())) {
            //
        }
    }
    /*    *//**
             * Create schedule policy and persist into CoprHD DB.
             * 
             * @param id the URN of a CoprHD Tenant/Subtenant
             * @param param schedule policy parameters
             * @brief Create schedule policy
             * @return No data returned in response body
             * @throws BadRequestException
             */
    /*
     * @POST
     * 
     * @Path("/{id}/schedule-policies")
     * 
     * @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
     * 
     * @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
     * 
     * @CheckPermission(roles = { Role.TENANT_ADMIN })
     * public SchedulePolicyResp createSchedulePolicy(@PathParam("id") URI id, PolicyParam param) {
     * SchedulePolicyResp schedulePolicyResp = createPolicy(id, param);
     * auditOp(OperationTypeEnum.CREATE_SCHEDULE_POLICY, true, null, param.getPolicyName(),
     * id.toString(), schedulePolicyResp.getId().toString());
     * return schedulePolicyResp;
     * }
     * 
     *//**
       * Gets the policyIds, policyNames and self links for all schedule policies.
       * 
       * @param id the URN of a CoprHD Tenant/Subtenant
       * @brief List schedule policies
       * @return policyList - A SchedulePolicyList reference specifying the policyIds, name and self links for
       *         the schedule policies.
       *//*
         * @GET
         * 
         * @Path("/{id}/schedule-policies")
         * 
         * @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
         * 
         * @CheckPermission(roles = { Role.TENANT_ADMIN, Role.PROJECT_ADMIN })
         * public SchedulePolicyList getSchedulePolicies(@PathParam("id") URI id) {
         * // TenantOrg tenant = getTenantById(id, false);
         * StorageOSUser user = getUserFromContext();
         * NamedElementQueryResultList schedulePolicies = new NamedElementQueryResultList();
         * if (_permissionsHelper.userHasGivenRole(user, tenant.getId(),
         * Role.SYSTEM_MONITOR, Role.TENANT_ADMIN, Role.SECURITY_ADMIN)) {
         * // list all schedule policies
         * _dbClient.queryByConstraint(ContainmentConstraint.Factory
         * .getTenantOrgSchedulePolicyConstraint(tenant.getId()), schedulePolicies);
         * } else {
         * // list only schedule policies that the user has access to
         * if (!id.equals(URI.create(user.getTenantId()))) {
         * throw APIException.forbidden.insufficientPermissionsForUser(user
         * .getName());
         * }
         * Map<URI, Set<String>> allMySchedulePolicies = _permissionsHelper.getAllPermissionsForUser(user, tenant.getId(),
         * null, false);
         * if (!allMySchedulePolicies.keySet().isEmpty()) {
         * List<SchedulePolicy> policyList = _dbClient.queryObjectField(SchedulePolicy.class, "label",
         * new ArrayList<URI>(allMySchedulePolicies.keySet()));
         * List<NamedElementQueryResultList.NamedElement> elements = new ArrayList<NamedElementQueryResultList.NamedElement>(
         * policyList.size());
         * for (SchedulePolicy policy : policyList) {
         * elements.add(NamedElementQueryResultList.NamedElement.createElement(
         * policy.getId(), policy.getLabel()));
         * }
         * schedulePolicies.setResult(elements.iterator());
         * } else {
         * // empty list
         * schedulePolicies.setResult(new ArrayList<NamedElementQueryResultList.NamedElement>()
         * .iterator());
         * }
         * }
         * SchedulePolicyList policyList = new SchedulePolicyList();
         * for (NamedElementQueryResultList.NamedElement el : schedulePolicies) {
         * policyList.getSchdulePolicies().add(
         * toNamedRelatedResource(ResourceTypeEnum.SCHEDULE_POLICY, el.getId(), el.getName()));
         * }
         * return policyList;
         * }
         */
}

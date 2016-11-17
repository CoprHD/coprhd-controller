/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

/**
 * @author jainm15
 */
import static com.emc.storageos.api.mapper.DbObjectMapper.toLink;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.impl.resource.utils.FilePolicyServiceUtils;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FilePolicy.SnapshotExpireType;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.SchedulePolicy;
import com.emc.storageos.db.client.model.SchedulePolicy.SchedulePolicyType;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.file.FilePolicyParam;
import com.emc.storageos.model.schedulepolicy.PolicyParam;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyList;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyResp;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;

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
        if (!ArgValidator.isValidEnum(param.getPolicyType(), FilePolicyType.class)) {
            throw APIException.badRequests.invalidSchedulePolicyType(param.getPolicyType());
        }
        _log.info("file policy creation started -- ");

        StringBuilder errorMsg = new StringBuilder();

        /**
         * Replication Policy Checks and creation.
         */
        if (param.getPolicyType().equals(FilePolicyParam.PolicyType.file_replication.name())) {
            FilePolicy fileReplicationPolicy = new FilePolicy();

            // Validate replication policy schedule parameters
            boolean isValidSchedule = FilePolicyServiceUtils.validatePolicySchdeuleParam(
                    param.getReplicationPolicyParams().getPolicySchedule(), fileReplicationPolicy, errorMsg);
            if (!isValidSchedule && errorMsg.length() > 0) {
                _log.error("Failed to create file replication policy due to {} ", errorMsg.toString());
                throw APIException.badRequests.invalidSchedulePolicyParam(param.getPolicyName(), errorMsg.toString());
            }

            fileReplicationPolicy.setId(URIUtil.createId(FilePolicy.class));
            fileReplicationPolicy.setFilePolicyName(param.getPolicyName());
            fileReplicationPolicy.setFileReplicationType(param.getReplicationPolicyParams().getReplicationType());
            fileReplicationPolicy.setFileReplicationCopyType(param.getReplicationPolicyParams().getReplicationCopyType());
            _dbClient.createObject(fileReplicationPolicy);
            _log.info("Policy {} created successfully", fileReplicationPolicy);
        }
        /**
         * Snapshot Policy checks and creation.
         */
        else if (param.getPolicyType().equals(FilePolicyParam.PolicyType.file_snapshot.name())) {
            FilePolicy fileSnapshotPolicy = new FilePolicy();

            // Validate snapshot policy schedule parameters
            boolean isValidSchedule = FilePolicyServiceUtils.validatePolicySchdeuleParam(
                    param.getSnapshotPolicyPrams().getPolicySchedule(), fileSnapshotPolicy, errorMsg);
            if (!isValidSchedule && errorMsg.length() > 0) {
                _log.error("Failed to create file snapshot policy due to {} ", errorMsg.toString());
                throw APIException.badRequests.invalidSchedulePolicyParam(param.getPolicyName(), errorMsg.toString());
            }

            // Validate snapshot policy expire parameters..
            if (param.getSnapshotPolicyPrams().getSnapshotExpireParams() != null) {
                FilePolicyServiceUtils.validateSnapshotPolicyParam(param.getSnapshotPolicyPrams());
            } else {
                errorMsg.append("Required parameter snapshot_expire was missing or empty");
                _log.error("Failed to create snapshot policy due to {} ", errorMsg.toString());
                throw APIException.badRequests.invalidSchedulePolicyParam(param.getPolicyName(), errorMsg.toString());
            }
            fileSnapshotPolicy.setId(URIUtil.createId(FilePolicy.class));
            fileSnapshotPolicy.setFilePolicyType(param.getPolicyType());
            fileSnapshotPolicy.setFilePolicyName(param.getPolicyName());
            fileSnapshotPolicy.setScheduleFrequency(param.getSnapshotPolicyPrams().getPolicySchedule().getScheduleFrequency());
            fileSnapshotPolicy.setSnapshotExpireType(param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireType());

            if (!param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireType()
                    .equalsIgnoreCase(SnapshotExpireType.NEVER.toString())) {
                fileSnapshotPolicy.setSnapshotExpireTime((long) param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireValue());
            }
            _dbClient.createObject(fileSnapshotPolicy);
            _log.info("Snapshot policy {} created successfully", fileSnapshotPolicy);

            /**
             * Quota Policy Creation and params checks.
             */
        } else if (param.getPolicyType().equals(FilePolicyParam.PolicyType.file_quota.name())) {
            //
        }
        return param;
    }

    /**
     * Create schedule policy and persist into CoprHD DB.
     * 
     * @param id the URN of a CoprHD Tenant/Subtenant
     * @param param schedule policy parameters
     * @brief Create schedule policy
     * @return No data returned in response body
     * @throws BadRequestException
     */
    @POST
    @Path("/{id}/schedule-policies")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public SchedulePolicyResp createSchedulePolicy(@PathParam("id") URI id, PolicyParam param) {
        SchedulePolicyResp schedulePolicyResp = createPolicy(id, param);
        auditOp(OperationTypeEnum.CREATE_SCHEDULE_POLICY, true, null, param.getPolicyName(),
                id.toString(), schedulePolicyResp.getId().toString());
        return schedulePolicyResp;
    }

    /**
     * Worker method for create schedule policy. Allows external requests (REST) as well as
     * internal requests that may not have a security context.
     * 
     * @param id the URN of a CoprHD Tenant/Subtenant
     * @param param schedule policy parameters
     * @brief Create schedule policy
     * @return No data returned in response body
     * @throws BadRequestException
     */
    public SchedulePolicyResp createPolicy(URI id, PolicyParam param) {
        TenantOrg tenant = getTenantById(id, true);

        // Make policy name as mandatory field
        ArgValidator.checkFieldNotNull(param.getPolicyName(), "policyName");

        // Check for duplicate policy name
        if (param.getPolicyName() != null && !param.getPolicyName().isEmpty()) {
            checkForDuplicateName(param.getPolicyName(), SchedulePolicy.class);
        }

        // check schedule policy type is valid or not
        if (!ArgValidator.isValidEnum(param.getPolicyType(), SchedulePolicyType.class)) {
            throw APIException.badRequests.invalidSchedulePolicyType(param.getPolicyType());
        }

        _log.info("Schedule policy creation started -- ");
        SchedulePolicy schedulePolicy = new SchedulePolicy();
        StringBuilder errorMsg = new StringBuilder();

        // Validate Schedule policy parameters
        boolean isValidSchedule = SchedulePolicyService.validateSchedulePolicyParam(param.getPolicySchedule(), schedulePolicy, errorMsg);
        if (!isValidSchedule && errorMsg != null && errorMsg.length() > 0) {
            _log.error("Failed to create schedule policy due to {} ", errorMsg.toString());
            throw APIException.badRequests.invalidSchedulePolicyParam(param.getPolicyName(), errorMsg.toString());
        }

        // Validate snapshot expire parameters
        boolean isValidSnapshotExpire = false;
        if (param.getSnapshotExpire() != null) {
            // check snapshot expire type is valid or not
            String expireType = param.getSnapshotExpire().getExpireType();
            if (!ArgValidator.isValidEnum(expireType, SnapshotExpireType.class)) {
                _log.error(
                        "Invalid schedule snapshot expire type {}. Valid Snapshot expire types are hours, days, weeks, months and never",
                        expireType);
                throw APIException.badRequests.invalidScheduleSnapshotExpireType(expireType);
            }
            isValidSnapshotExpire = SchedulePolicyService.validateSnapshotExpireParam(param.getSnapshotExpire());
            if (!isValidSnapshotExpire) {
                int expireTime = param.getSnapshotExpire().getExpireValue();
                int minExpireTime = 2;
                int maxExpireTime = 10;
                _log.error("Invalid schedule snapshot expire time {}. Try an expire time between {} hours to {} years",
                        expireTime, minExpireTime, maxExpireTime);
                throw APIException.badRequests.invalidScheduleSnapshotExpireValue(expireTime, minExpireTime, maxExpireTime);
            }
        } else {
            if (param.getPolicyType().equalsIgnoreCase(SchedulePolicyType.file_snapshot.toString())) {
                errorMsg.append("Required parameter snapshot_expire was missing or empty");
                _log.error("Failed to create schedule policy due to {} ", errorMsg.toString());
                throw APIException.badRequests.invalidSchedulePolicyParam(param.getPolicyName(), errorMsg.toString());
            }
        }

        if (isValidSchedule) {
            schedulePolicy.setId(URIUtil.createId(SchedulePolicy.class));
            schedulePolicy.setPolicyType(param.getPolicyType());
            schedulePolicy.setLabel(param.getPolicyName());
            schedulePolicy.setPolicyName(param.getPolicyName());
            schedulePolicy.setScheduleFrequency(param.getPolicySchedule().getScheduleFrequency().toLowerCase());
            if (isValidSnapshotExpire) {
                schedulePolicy.setSnapshotExpireType(param.getSnapshotExpire().getExpireType().toLowerCase());
                if (!param.getSnapshotExpire().getExpireType().equalsIgnoreCase(SnapshotExpireType.NEVER.toString())) {
                    schedulePolicy.setSnapshotExpireTime((long) param.getSnapshotExpire().getExpireValue());
                }
            }
            schedulePolicy.setTenantOrg(new NamedURI(tenant.getId(), schedulePolicy.getLabel()));
            _dbClient.createObject(schedulePolicy);
            _log.info("Schedule policy {} created successfully", schedulePolicy);
        }
        recordTenantEvent(OperationTypeEnum.CREATE_SCHEDULE_POLICY, tenant.getId(),
                schedulePolicy.getId());

        return new SchedulePolicyResp(schedulePolicy.getId(), toLink(ResourceTypeEnum.SCHEDULE_POLICY,
                schedulePolicy.getId()), schedulePolicy.getLabel());
    }

    /**
     * Gets the policyIds, policyNames and self links for all schedule policies.
     * 
     * @param id the URN of a CoprHD Tenant/Subtenant
     * @brief List schedule policies
     * @return policyList - A SchedulePolicyList reference specifying the policyIds, name and self links for
     *         the schedule policies.
     */
    @GET
    @Path("/{id}/schedule-policies")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN, Role.PROJECT_ADMIN })
    public SchedulePolicyList getSchedulePolicies(@PathParam("id") URI id) {
        TenantOrg tenant = getTenantById(id, false);
        StorageOSUser user = getUserFromContext();
        NamedElementQueryResultList schedulePolicies = new NamedElementQueryResultList();
        if (_permissionsHelper.userHasGivenRole(user, tenant.getId(),
                Role.SYSTEM_MONITOR, Role.TENANT_ADMIN, Role.SECURITY_ADMIN)) {
            // list all schedule policies
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getTenantOrgSchedulePolicyConstraint(tenant.getId()), schedulePolicies);
        } else {
            // list only schedule policies that the user has access to
            if (!id.equals(URI.create(user.getTenantId()))) {
                throw APIException.forbidden.insufficientPermissionsForUser(user
                        .getName());
            }
            Map<URI, Set<String>> allMySchedulePolicies = _permissionsHelper.getAllPermissionsForUser(user, tenant.getId(),
                    null, false);
            if (!allMySchedulePolicies.keySet().isEmpty()) {
                List<SchedulePolicy> policyList = _dbClient.queryObjectField(SchedulePolicy.class, "label",
                        new ArrayList<URI>(allMySchedulePolicies.keySet()));
                List<NamedElementQueryResultList.NamedElement> elements = new ArrayList<NamedElementQueryResultList.NamedElement>(
                        policyList.size());
                for (SchedulePolicy policy : policyList) {
                    elements.add(NamedElementQueryResultList.NamedElement.createElement(
                            policy.getId(), policy.getLabel()));
                }
                schedulePolicies.setResult(elements.iterator());
            } else {
                // empty list
                schedulePolicies.setResult(new ArrayList<NamedElementQueryResultList.NamedElement>()
                        .iterator());
            }
        }
        SchedulePolicyList policyList = new SchedulePolicyList();
        for (NamedElementQueryResultList.NamedElement el : schedulePolicies) {
            policyList.getSchdulePolicies().add(
                    toNamedRelatedResource(ResourceTypeEnum.SCHEDULE_POLICY, el.getId(), el.getName()));
        }
        return policyList;
    }
}

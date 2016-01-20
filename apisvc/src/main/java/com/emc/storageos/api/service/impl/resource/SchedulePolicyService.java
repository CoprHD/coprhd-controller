/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.map;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.functions.MapSchedulePolicy;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.SchedulePolicy;
import com.emc.storageos.db.client.model.SchedulePolicy.SchedulePolicyType;
import com.emc.storageos.db.client.model.SchedulePolicy.SnapshotExpireType;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.schedulepolicy.PolicyParam;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyBulkRep;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyRestRep;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;

/**
 * SchedulePolicyService resource implementation
 * 
 * @author prasaa9
 * 
 */
@Path("/schedule-policies")
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.TENANT_ADMIN })
public class SchedulePolicyService extends TaggedResource {

    private static final Logger _log = LoggerFactory.getLogger(SchedulePolicyService.class);

    protected static final String EVENT_SERVICE_SOURCE = "SchedulePolicyService";

    private static final String EVENT_SERVICE_TYPE = "schedulePolicy";

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    private NetworkService networkSvc;

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.SCHEDULE_POLICY;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<SchedulePolicy> getResourceClass() {
        return SchedulePolicy.class;
    }

    /**
     * Get project object from id
     * 
     * @param id the URN of a ViPR Project
     * @return
     */
    private SchedulePolicy getPolicyById(URI id, boolean checkInactive) {
        if (id == null) {
            return null;
        }

        SchedulePolicy schedulePolicy = _permissionsHelper.getObjectById(id, SchedulePolicy.class);
        ArgValidator.checkEntity(schedulePolicy, id, isIdEmbeddedInURL(id));
        return schedulePolicy;
    }

    @Override
    protected SchedulePolicy queryResource(URI id) {
        ArgValidator.checkUri(id);
        return getPolicyById(id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        SchedulePolicy schedulePolicy = queryResource(id);
        return schedulePolicy.getTenantOrg().getURI();
    }

    /**
     * Get the details of a schedule policy.
     * 
     * @param policyId the URN of a schedule policy.
     * 
     * @brief Show schedule policy
     * @return A SchedulePolicyRestRep reference specifying the data for the
     *         schedule policy with the passed policyId.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public SchedulePolicyRestRep getSchedulePolicy(@PathParam("id") URI policyId) {
        ArgValidator.checkFieldUriType(policyId, SchedulePolicy.class, "policyId");
        SchedulePolicy schedulePolicy = queryResource(policyId);
        return map(schedulePolicy);
    }

    /**
     * Update info for schedule policy including name, schedule, snapshot expire etc.
     * 
     * @param policyId the URN of a schedule policy
     * @param param schedule policy update parameters
     * @brief Update schedule policy
     * @return No data returned in response body
     * @throws BadRequestException
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public Response updateSchedulePolicy(@PathParam("id") URI policyId, PolicyParam param) {

        // check policy
        ArgValidator.checkFieldUriType(policyId, SchedulePolicy.class, "policyId");

        // check schedule policy type is valid or not
        if (!ArgValidator.isValidEnum(param.getPolicyType(), SchedulePolicyType.class)) {
            throw APIException.badRequests.invalidSchedulePolicyType(param.getPolicyType());
        }

        SchedulePolicy schedulePolicy = getPolicyById(policyId, true);
        _log.info("Schedule policy {} update started", schedulePolicy.getPolicyName());
        if (null != param.getPolicyName() && !param.getPolicyName().isEmpty() &&
                !schedulePolicy.getLabel().equalsIgnoreCase(param.getPolicyName())) {
            checkForDuplicateName(param.getPolicyName(), SchedulePolicy.class, schedulePolicy.getTenantOrg()
                    .getURI(), "tenantOrg", _dbClient);
            NamedURI tenant = schedulePolicy.getTenantOrg();
            if (tenant != null) {
                tenant.setName(param.getPolicyName());
                schedulePolicy.setTenantOrg(tenant);
            }
        }

        // Validate Schedule policy parameters
        StringBuilder errorMsg = new StringBuilder();
        boolean isValidSchedule = ArgValidator.validateSchedulePolicyParam(param.getPolicySchedule(), schedulePolicy, errorMsg);
        if (errorMsg != null && errorMsg.length() > 0) {
            _log.error("Failed to update schedule policy due to {} ", errorMsg.toString());
            throw APIException.badRequests.invalidSchedulePolicyParam(param.getPolicyName(), errorMsg.toString());
        }

        // Validate snapshot expire parameters
        boolean isValidSnapshotExpire = false;
        if (param.getSnapshotExpire() != null) {
            String expireType = param.getSnapshotExpire().getExpireType();
            if (!ArgValidator.isValidEnum(expireType, SnapshotExpireType.class)) {
                _log.error("Invalid schedule snapshot expire type {}. Valid Snapshot expire types are hours, days, weeks and months",
                        expireType);
                throw APIException.badRequests.invalidScheduleSnapshotExpireType(expireType);
            }
            isValidSnapshotExpire = ArgValidator.validateSnapshotExpireParam(param.getSnapshotExpire());
            if (!isValidSnapshotExpire) {
                int expireTime = param.getSnapshotExpire().getExpireValue();
                _log.error("Invalid schedule snapshot expire time {}. Try an expire time between 2 hours to 10 years",
                        expireTime);
                throw APIException.badRequests.invalidScheduleSnapshotExpireValue(expireTime);
            }
        }

        if (isValidSchedule) {
            schedulePolicy.setPolicyType(param.getPolicyType());
            schedulePolicy.setLabel(param.getPolicyName());
            schedulePolicy.setPolicyName(param.getPolicyName());
            schedulePolicy.setScheduleFrequency(param.getPolicySchedule().getScheduleFrequency());
            if (isValidSnapshotExpire) {
                schedulePolicy.setSnapshotExpireType(param.getSnapshotExpire().getExpireType());
                schedulePolicy.setSnapshotExpireTime((long) param.getSnapshotExpire().getExpireValue());
            }
            _dbClient.updateObject(schedulePolicy);
            _log.info("Schedule policy {} updated successfully", schedulePolicy.getPolicyName());
        }
        return Response.ok().build();
    }

    /**
     * Delete schedule policy from CoprHD DB.
     * 
     * @param policyId the URN of a schedule policy
     * @brief Delete schedule policy from CoprHD DB
     * @return No data returned in response body
     * @throws BadRequestException
     */
    @DELETE
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public Response deleteSchedulePolicy(@PathParam("id") URI policyId) {
        ArgValidator.checkFieldUriType(policyId, SchedulePolicy.class, "policyId");
        SchedulePolicy schedulePolicy = getPolicyById(policyId, true);
        ArgValidator.checkReference(SchedulePolicy.class, policyId, checkForDelete(schedulePolicy));
        _dbClient.markForDeletion(schedulePolicy);
        _log.info("Schedule policy {} deleted successfully", schedulePolicy.getPolicyName());
        return Response.ok().build();
    }

    @Override
    public SchedulePolicyBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<SchedulePolicy> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        return new SchedulePolicyBulkRep(BulkList.wrapping(_dbIterator,
                MapSchedulePolicy.getInstance(_dbClient)));
    }

    @Override
    public SchedulePolicyBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }
}

/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.map;

import java.net.URI;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
import com.emc.storageos.db.client.model.SchedulePolicy.ScheduleFrequency;
import com.emc.storageos.db.client.model.SchedulePolicy.SchedulePolicyType;
import com.emc.storageos.db.client.model.SchedulePolicy.SnapshotExpireType;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.file.ScheduleSnapshotExpireParam;
import com.emc.storageos.model.schedulepolicy.PolicyParam;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyBulkRep;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyParam;
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
     * Get schedule policy object from id
     * 
     * @param id the URN of a CoprHD Schedule Policy
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

        // Check policy is associated with FS before update
        SchedulePolicy schedulePolicy = getPolicyById(policyId, true);
        StringSet resources = schedulePolicy.getAssignedResources();
        if (resources != null && !resources.isEmpty()) {
            _log.error("Unable to update schedule policy {} as it is already associated with resources",
                    schedulePolicy.getPolicyName());
            throw APIException.badRequests.unableToUpdateSchedulePolicy(schedulePolicy.getPolicyName());
        }

        // check schedule policy type is valid or not
        if (!ArgValidator.isValidEnum(param.getPolicyType(), SchedulePolicyType.class)) {
            throw APIException.badRequests.invalidSchedulePolicyType(param.getPolicyType());
        }

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
        boolean isValidSchedule = validateSchedulePolicyParam(param.getPolicySchedule(), schedulePolicy, errorMsg);
        if (!isValidSchedule && errorMsg != null && errorMsg.length() > 0) {
            _log.error("Failed to update schedule policy due to {} ", errorMsg.toString());
            throw APIException.badRequests.invalidSchedulePolicyParam(param.getPolicyName(), errorMsg.toString());
        }

        // Validate snapshot expire parameters
        boolean isValidSnapshotExpire = false;
        if (param.getSnapshotExpire() != null) {
            String expireType = param.getSnapshotExpire().getExpireType();
            if (!ArgValidator.isValidEnum(expireType, SnapshotExpireType.class)) {
                _log.error(
                        "Invalid schedule snapshot expire type {}. Valid Snapshot expire types are hours, days, weeks, months and never",
                        expireType);
                throw APIException.badRequests.invalidScheduleSnapshotExpireType(expireType);
            }
            isValidSnapshotExpire = validateSnapshotExpireParam(param.getSnapshotExpire());
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
                _log.error("Failed to update schedule policy due to {} ", errorMsg.toString());
                throw APIException.badRequests.invalidSchedulePolicyParam(param.getPolicyName(), errorMsg.toString());
            }
        }

        if (isValidSchedule) {
            schedulePolicy.setPolicyType(param.getPolicyType());
            schedulePolicy.setLabel(param.getPolicyName());
            schedulePolicy.setPolicyName(param.getPolicyName());
            schedulePolicy.setScheduleFrequency(param.getPolicySchedule().getScheduleFrequency().toLowerCase());
            if (isValidSnapshotExpire) {
                schedulePolicy.setSnapshotExpireType(param.getSnapshotExpire().getExpireType().toLowerCase());
                if (!param.getSnapshotExpire().getExpireType().equalsIgnoreCase(SnapshotExpireType.NEVER.toString())) {
                    schedulePolicy.setSnapshotExpireTime((long) param.getSnapshotExpire().getExpireValue());
                } else {
                    schedulePolicy.setSnapshotExpireTime(0L);
                }
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

        // Check policy is associated with FS before delete
        StringSet resources = schedulePolicy.getAssignedResources();
        if (resources != null && !resources.isEmpty()) {
            _log.error("Unable to delete schedule policy {} as it is already associated with resources",
                    schedulePolicy.getPolicyName());
            throw APIException.badRequests.unableToDeleteSchedulePolicy(schedulePolicy.getPolicyName());
        }
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
        Iterator<SchedulePolicy> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.SchedulePolicyFilter(getUserFromContext(), _permissionsHelper);
        return new SchedulePolicyBulkRep(BulkList.wrapping(_dbIterator,
                MapSchedulePolicy.getInstance(_dbClient), filter));
    }

    /**
     * validates whether the schedule policy parameters are valid or not
     * 
     * @param schedule - schedule policy parameters
     * @param schedulePolicy - schedulePolicy object to set schedule values
     * @param errorMsg - error message
     * @return true/false
     */
    public static boolean validateSchedulePolicyParam(SchedulePolicyParam schedule, SchedulePolicy schedulePolicy, StringBuilder errorMsg) {

        if (schedule != null) {

            // check schedule frequency is valid or not
            if (!ArgValidator.isValidEnum(schedule.getScheduleFrequency(), ScheduleFrequency.class)) {
                errorMsg.append("Schedule frequency: " + schedule.getScheduleFrequency()
                        + " is invalid. Valid schedule frequencies are days, weeks and months");
                return false;
            }

            // validating schedule repeat period
            if (schedule.getScheduleRepeat() < 1) {
                errorMsg.append("required parameter schedule_repeat is missing or value: " + schedule.getScheduleRepeat()
                        + " is invalid");
                return false;
            }

            // validating schedule time
            String period = " PM";
            int hour, minute;
            boolean isValid = true;
            if (schedule.getScheduleTime().contains(":")) {
                String splitTime[] = schedule.getScheduleTime().split(":");
                hour = Integer.parseInt(splitTime[0]);
                minute = Integer.parseInt(splitTime[1]);
                if (splitTime[0].startsWith("-") || splitTime[1].startsWith("-")) {
                    isValid = false;
                }
            } else {
                hour = Integer.parseInt(schedule.getScheduleTime());
                minute = 0;
            }
            if (isValid && (hour >= 0 && hour < 24) && (minute >= 0 && minute < 60)) {
                if (hour < 12) {
                    period = " AM";
                }
            } else {
                errorMsg.append("Schedule time: " + schedule.getScheduleTime() + " is invalid");
                return false;
            }

            ScheduleFrequency scheduleFreq = ScheduleFrequency.valueOf(schedule.getScheduleFrequency().toUpperCase());
            switch (scheduleFreq) {

                case DAYS:
                    schedulePolicy.setScheduleRepeat((long) schedule.getScheduleRepeat());
                    schedulePolicy.setScheduleTime(schedule.getScheduleTime() + period);
                    if (schedulePolicy.getScheduleDayOfWeek() != null && !schedulePolicy.getScheduleDayOfWeek().isEmpty()) {
                        schedulePolicy.setScheduleDayOfWeek(NullColumnValueGetter.getNullStr());
                    }
                    if (schedulePolicy.getScheduleDayOfMonth() != null) {
                        schedulePolicy.setScheduleDayOfMonth(0L);
                    }
                    break;
                case WEEKS:
                    schedulePolicy.setScheduleRepeat((long) schedule.getScheduleRepeat());
                    if (schedule.getScheduleDayOfWeek() != null && !schedule.getScheduleDayOfWeek().isEmpty()) {
                        List<String> weeks = Arrays.asList("monday", "tuesday", "wednesday", "thursday", "friday",
                                "saturday", "sunday");
                        if (weeks.contains(schedule.getScheduleDayOfWeek().toLowerCase())) {
                            schedulePolicy.setScheduleDayOfWeek(schedule.getScheduleDayOfWeek().toLowerCase());
                        } else {
                            errorMsg.append("Schedule day of week: " + schedule.getScheduleDayOfWeek() + " is invalid");
                            return false;
                        }
                    } else {
                        errorMsg.append("required parameter schedule_day_of_week was missing or empty");
                        return false;
                    }
                    schedulePolicy.setScheduleTime(schedule.getScheduleTime() + period);
                    if (schedulePolicy.getScheduleDayOfMonth() != null) {
                        schedulePolicy.setScheduleDayOfMonth(0L);
                    }
                    break;
                case MONTHS:
                    if (schedule.getScheduleDayOfMonth() > 0 && schedule.getScheduleDayOfMonth() <= 31) {
                        schedulePolicy.setScheduleDayOfMonth((long) schedule.getScheduleDayOfMonth());
                        schedulePolicy.setScheduleRepeat((long) schedule.getScheduleRepeat());
                        schedulePolicy.setScheduleTime(schedule.getScheduleTime() + period);
                        if (schedulePolicy.getScheduleDayOfWeek() != null) {
                            schedulePolicy.setScheduleDayOfWeek(NullColumnValueGetter.getNullStr());
                        }
                    } else {
                        errorMsg.append("required parameter schedule_day_of_month is missing or value: " + schedule.getScheduleDayOfMonth()
                                + " is invalid");
                        return false;
                    }
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    /**
     * validates whether the snapshot expire parameters are valid or not
     * 
     * @param expireParam - snapshot expire parameters
     * @return true/false
     */
    public static boolean validateSnapshotExpireParam(ScheduleSnapshotExpireParam expireParam) {

        long seconds = 0;
        long minPeriod = 7200;
        long maxPeriod = 10 * 365 * 24 * 3600;
        int expireValue = expireParam.getExpireValue();
        SnapshotExpireType expireType = SnapshotExpireType.valueOf(expireParam.getExpireType().toUpperCase());
        switch (expireType) {
            case HOURS:
                seconds = TimeUnit.HOURS.toSeconds(expireValue);
                break;
            case DAYS:
                seconds = TimeUnit.DAYS.toSeconds(expireValue);
                break;
            case WEEKS:
                seconds = TimeUnit.DAYS.toSeconds(expireValue * 7);
                break;
            case MONTHS:
                seconds = TimeUnit.DAYS.toSeconds(expireValue * 30);
                break;
            case NEVER:
                return true;
            default:
                return false;
        }
        if (seconds >= minPeriod && seconds <= maxPeriod) {
            return true;
        }
        return false;
    }

}

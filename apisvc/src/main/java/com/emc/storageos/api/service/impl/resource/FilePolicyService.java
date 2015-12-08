/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

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

import com.emc.storageos.api.mapper.functions.MapFilePolicy;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.file.FilePolicyBulkRep;
import com.emc.storageos.model.file.FilePolicyExpireParam;
import com.emc.storageos.model.file.FilePolicyParam;
import com.emc.storageos.model.file.FilePolicyRestRep;
import com.emc.storageos.model.file.FilePolicyScheduleParam;
import com.emc.storageos.model.file.FileSchedulePolicyList;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;

/**
 * FilePolicyService resource implementation
 * 
 * @author prasaa9
 * 
 */
@Path("/file/file-policies")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class FilePolicyService extends TaggedResource {

    private static final Logger _log = LoggerFactory.getLogger(FilePolicyService.class);

    protected static final String EVENT_SERVICE_SOURCE = "FilePolicyService";

    private static final String EVENT_SERVICE_TYPE = "filePolicy";

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
        return ResourceTypeEnum.FILE_POLICY;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<FilePolicy> getResourceClass() {
        return FilePolicy.class;
    }

    @Override
    protected DataObject queryResource(URI id) {
        ArgValidator.checkUri(id);
        FilePolicy filePolicy = _dbClient.queryObject(FilePolicy.class, id);
        ArgValidator.checkEntity(filePolicy, id, isIdEmbeddedInURL(id));
        return filePolicy;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    /**
     * Gets the policyIds and self links for all file schedule policies.
     * 
     * @brief List file schedule policies
     * @return A FileSchedulePolicyList reference specifying the policyIds and self links for
     *         the file schedule policies.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public FileSchedulePolicyList getFilePolicies() {

        FileSchedulePolicyList policyList = new FileSchedulePolicyList();
        List<URI> policyIds = _dbClient.queryByType(FilePolicy.class, true);
        for (URI policyId : policyIds) {
            FilePolicy filePolicy = _dbClient.queryObject(FilePolicy.class, policyId);
            if ((filePolicy != null)) {
                policyList.getSchdulePolicies().add(
                        toNamedRelatedResource(filePolicy, filePolicy.getPolicyName()));
            }
        }
        return policyList;
    }

    /**
     * Gets the details of file schedule policy.
     * 
     * @param policyId the URN of a file schedule policy.
     * 
     * @brief Show file schedule policy
     * @return A FilePolicyRestRep reference specifying the data for the
     *         file schedule policy with the passed policyId.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public FilePolicyRestRep getFilePolicy(@PathParam("id") URI policyId) {
        ArgValidator.checkFieldUriType(policyId, FilePolicy.class, "policyId");
        FilePolicyRestRep policy = new FilePolicyRestRep();
        FilePolicy filePolicy = _dbClient.queryObject(FilePolicy.class, policyId);
        if (filePolicy != null) {
            policy.setPolicyId(filePolicy.getId());
            policy.setPolicyName(filePolicy.getPolicyName());
            policy.setPolicySchedule(filePolicy.getPolicySchedule());
            policy.setPolicyExipration(filePolicy.getPolicyExpire());
        }
        return policy;
    }

    /**
     * Create file schedule policy and persist into CoprHD DB.
     * 
     * @param param File schedule policy parameters
     * @brief Create file schedule policy
     * @return No data returned in response body
     * @throws BadRequestException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public Response createFilePolicy(FilePolicyParam param) {
        if (param.getPolicyName() != null && !param.getPolicyName().isEmpty()) {
            checkForDuplicateName(param.getPolicyName(), FilePolicy.class);
        }
        _log.info("File schedule policy creation started -- ");
        FilePolicy filePolicy = new FilePolicy();
        String schedule = validatePolicySchedule(param.getPolicySchedule());
        if (schedule.contains("invalid input")) {
            // throw APIException.badRequests.vNasServersNotAssociatedToProject();
        }
        Long policyExpire = validatePolicyExpire(param.getPolicyExpire());
        if (policyExpire == 0) {
            // throw APIException.badRequests.vNasServersNotAssociatedToProject();
        }
        filePolicy.setId(URIUtil.createId(FilePolicy.class));
        filePolicy.setLabel(param.getPolicyName());
        filePolicy.setPolicyName(param.getPolicyName());
        filePolicy.setPolicySchedule(schedule);
        filePolicy.setPolicyExpire(policyExpire);
        _dbClient.createObject(filePolicy);
        _log.info("File schedule policy {} created successfully", filePolicy);
        return Response.ok().build();
    }

    /**
     * validates whether the schedule policy is valid or not
     * 
     * @param schedule - file policy schedule parameters
     * @return valid schedule policy
     */
    private String validatePolicySchedule(FilePolicyScheduleParam schedule) {
        StringBuilder builder = new StringBuilder();
        if (schedule != null) {
            String splitTime[] = schedule.getScheduleTime().split(":");
            int time = Integer.parseInt(splitTime[0]);
            String period = " PM";
            if (time < 12) {
                period = " AM";
            }
            switch (schedule.getScheduleType().toLowerCase()) {
                case "daily":
                    builder.append("every ");
                    builder.append(schedule.getScheduleNumber());
                    builder.append(" days at ");
                    builder.append(schedule.getScheduleTime());
                    builder.append(period);
                    break;
                case "weekly":
                    builder.append("every ");
                    builder.append(schedule.getScheduleNumber());
                    builder.append(" weeks on ");
                    builder.append(schedule.getScheduleDay());
                    builder.append(" at ");
                    builder.append(schedule.getScheduleTime());
                    builder.append(period);
                    break;
                case "monthly":
                    builder.append("the ");
                    builder.append(schedule.getScheduleNumber());
                    builder.append(" every ");
                    builder.append(schedule.getScheduleNumber());
                    builder.append(" month at ");
                    builder.append(schedule.getScheduleTime());
                    builder.append(period);
                    break;
                case "yearly":
                    builder.append("yearly on the ");
                    builder.append(schedule.getScheduleNumber());
                    builder.append(" of ");
                    builder.append(schedule.getScheduleMonth());
                    builder.append(" at ");
                    builder.append(schedule.getScheduleTime());
                    builder.append(period);
                    break;
                default:
                    builder.append("invalid input");
                    break;
            }
        }
        return builder.toString();
    }

    /**
     * validates whether the policy expire duration is valid or not
     * 
     * @param expireParam - file policy expire parameters
     * @return valid policy expire duration
     */
    private Long validatePolicyExpire(FilePolicyExpireParam expireParam) {

        if (expireParam != null) {
            long seconds = 0;
            long minPeriod = 7200;
            long maxPeriod = 10 * 365 * 24 * 3600;
            switch (expireParam.getExpireType().toLowerCase()) {
                case "hours":
                    seconds = expireParam.getExpireValue() * 3600;
                    break;
                case "days":
                    seconds = expireParam.getExpireValue() * 24 * 3600;
                    break;
                case "weeks":
                    seconds = expireParam.getExpireValue() * 7 * 24 * 3600;
                    break;
                case "months":
                    seconds = expireParam.getExpireValue() * 30 * 24 * 3600;
                    break;
                case "years":
                    seconds = expireParam.getExpireValue() * 365 * 24 * 3600;
                    break;
                default:
                    break;
            }
            if (seconds >= minPeriod && seconds <= maxPeriod) {
                return seconds;
            }
        }
        return 0L;
    }

    /**
     * Update info for file schedule policy including name, schedule etc.
     * 
     * @param policyId the URN of a file schedule policy
     * @param param file schedule policy update parameters
     * @brief Update file schedule policy
     * @return No data returned in response body
     * @throws BadRequestException
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public Response updateFilePolicy(@PathParam("id") URI policyId, FilePolicyParam param) {
        ArgValidator.checkFieldUriType(policyId, FilePolicy.class, "policyId");
        if (param.getPolicyName() != null && !param.getPolicyName().isEmpty()) {
            checkForDuplicateName(param.getPolicyName(), FilePolicy.class);
        }
        FilePolicy filePolicy = _dbClient.queryObject(FilePolicy.class, policyId);
        if (filePolicy != null) {
            String schedule = validatePolicySchedule(param.getPolicySchedule());
            if (schedule.contains("invalid input")) {
                // throw APIException.badRequests.vNasServersNotAssociatedToProject();
            }
            Long policyExpire = validatePolicyExpire(param.getPolicyExpire());
            if (policyExpire == 0) {
                // throw APIException.badRequests.vNasServersNotAssociatedToProject();
            }
            filePolicy.setLabel(param.getPolicyName());
            filePolicy.setPolicyName(param.getPolicyName());
            filePolicy.setPolicySchedule(schedule);
            filePolicy.setPolicyExpire(policyExpire);
            _dbClient.updateObject(filePolicy);
            _log.info("File schedule policy {} updated successfully", filePolicy.getPolicyName());
        }
        return Response.ok().build();
    }

    /**
     * Delete file schedule policy from CoprHD DB.
     * 
     * @param policyId the URN of a file schedule policy
     * @brief Delete file schedule policy from CoprHD DB
     * @return No data returned in response body
     * @throws BadRequestException
     */
    @DELETE
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public Response deleteFilePolicy(@PathParam("id") URI policyId) {
        ArgValidator.checkFieldUriType(policyId, FilePolicy.class, "policyId");
        FilePolicy filePolicy = _dbClient.queryObject(FilePolicy.class, policyId);
        _dbClient.markForDeletion(filePolicy);
        _log.info("File schedule policy {} deleted successfully", filePolicy.getPolicyName());
        return Response.ok().build();
    }

    @Override
    public FilePolicyBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<FilePolicy> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        return new FilePolicyBulkRep(BulkList.wrapping(_dbIterator,
                MapFilePolicy.getInstance(_dbClient)));
    }

    @Override
    public FilePolicyBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }
}

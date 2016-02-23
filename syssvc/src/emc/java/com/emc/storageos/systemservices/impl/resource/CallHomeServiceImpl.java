/*
 * Copyright (c) 2013-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.resource;

import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.toServiceCode;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceErrorFactory.toServiceErrorRestRep;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.CoordinatorClient.LicenseType;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.SysEvent;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.event.EventParameters;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.services.util.TimeUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ForbiddenException;
import com.emc.storageos.systemservices.impl.eventhandler.connectemc.BuildEsrsDevice;
import com.emc.storageos.systemservices.impl.eventhandler.connectemc.CallHomeConstants;
import com.emc.storageos.systemservices.impl.eventhandler.connectemc.CallHomeEventManager;
import com.emc.storageos.systemservices.impl.eventhandler.connectemc.CallHomeEventsFacade;
import com.emc.storageos.systemservices.impl.eventhandler.connectemc.SendAlertEvent;
import com.emc.storageos.systemservices.impl.licensing.LicenseInfoExt;
import com.emc.storageos.systemservices.impl.licensing.LicenseInfoListExt;
import com.emc.storageos.systemservices.impl.licensing.LicenseManager;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;

public class CallHomeServiceImpl extends BaseLogSvcResource implements CallHomeService {

    private BuildEsrsDevice _buildEsrsDevice;
    private CallHomeEventsFacade _callHomeEventsFacade;
    private LicenseManager _licenseManager;
    private CallHomeEventManager _callHomeEventManager;
    private static final int FIXED_THREAD_POOL_SIZE = 10;
    private static ExecutorService executorService = null;

    @Autowired
    private AuditLogManager _auditMgr;
    @Autowired
    private DbClient dbClient;
    @Autowired
    protected BasePermissionsHelper permissionsHelper = null;
    @Autowired
    private ServiceImpl serviceInfo;
    @Autowired
    private LogService logService;
    @Autowired
    CoordinatorClientExt coordinator;

    private static final Logger _log = LoggerFactory.getLogger(CallHomeServiceImpl.class);
    private static final String EVENT_SERVICE_TYPE = "callHome";

    private synchronized static ExecutorService getExecutorServiceInstance() {
        if (executorService == null) {
            executorService = Executors.newFixedThreadPool(FIXED_THREAD_POOL_SIZE);
        }
        return executorService;
    }

    @Override
    public TaskResourceRep sendInternalAlert(String source, int eventId, List<String> nodeIds, List<String> nodeNames,
            List<String> logNames, int severity, String start, String end, String msgRegex, int maxCount,
            EventParameters eventParameters) throws Exception {
        _log.info("Sending internal alert for id: {} and source: {}", eventId, source);
        return sendAlert(source, eventId, nodeIds, nodeNames, logNames, severity, start, end
                , msgRegex, maxCount, true, 1, eventParameters);
    }

    @Override
    public TaskResourceRep sendAlert(String source, int eventId, List<String> nodeIds, List<String> nodeNames,
            List<String> logNames, int severity, String start, String end, String msgRegex, int maxCount,
            boolean forceAttachLogs, int force, EventParameters eventParameters) throws Exception {
        if (LogService.runningRequests.get() >= LogService.MAX_THREAD_COUNT) {
            _log.info("Current running requests: {} vs maximum allowed {}",
                    LogService.runningRequests, LogService.MAX_THREAD_COUNT);
            throw APIException.serviceUnavailable.logServiceIsBusy();
        }

        // if not configured for callhome, do not continue.
        _callHomeEventManager.validateSendEvent();

        // validate event id
        if (!CallHomeConstants.VALID_ALERT_EVENT_IDS.contains(eventId)) {
            throw APIException.badRequests.parameterIsNotOneOfAllowedValues("event_id",
                    CallHomeConstants.VALID_ALERT_EVENT_IDS.toString());
        }
        LicenseInfoExt licenseInfo = null;
        // If the user specified a license type to use for sending alerts get it from coordinator
        // Otherwise check if controller or unstructured is licensed
        if (source != null && !source.isEmpty()) {
            // validate source
            LicenseType licenseType = LicenseType.findByValue(source);
            if (licenseType == null) {
                throw APIException.badRequests.parameterIsNotOneOfAllowedValues("source",
                        LicenseType.getValuesAsStrings().toString());
            }
            // get an instance of the requested license information
            licenseInfo = _licenseManager.getLicenseInfoFromCoordinator(licenseType);
            if (licenseInfo == null) {
                throw APIException.internalServerErrors.licenseInfoNotFoundForType(licenseType.toString());
            }
        } else {
            licenseInfo = _licenseManager.getLicenseInfoFromCoordinator(LicenseType.CONTROLLER);
            if (licenseInfo == null) {
                licenseInfo = _licenseManager.getLicenseInfoFromCoordinator(LicenseType.UNSTRUCTURED);
            }
        }

        if (licenseInfo == null) {
            throw ForbiddenException.forbidden.licenseNotFound(LicenseType.CONTROLLER.toString() + " or " +
                    LicenseType.UNSTRUCTURED.toString());
        }

        if (licenseInfo.isTrialLicense()) {
            _log.warn("Cannot send alert to SYR for trial license {} ",
                    licenseInfo.getLicenseType().toString());
            throw APIException.forbidden.permissionDeniedForTrialLicense(
                    licenseInfo.getLicenseType().toString());
        }

        // invoke get-logs api for the dry run
        List<String> logNamesToUse = getLogNamesFromAlias(logNames);
        try {
            logService.getLogs(nodeIds, nodeNames, logNamesToUse, severity, start,
                    end, msgRegex, maxCount, true);
        } catch (Exception e) {
            _log.error("Failed to dry run get-logs, exception: {}", e);
            throw e;
        }

        URI sysEventId = URIUtil.createId(SysEvent.class);
        String opID = UUID.randomUUID().toString();
        SendAlertEvent sendAlertEvent = new SendAlertEvent(serviceInfo,
                dbClient, _logSvcPropertiesLoader, sysEventId, opID,
                getMediaType(), licenseInfo, permissionsHelper, coordinator);
        sendAlertEvent.setEventId(eventId);
        sendAlertEvent.setNodeIds(nodeIds);
        sendAlertEvent.setLogNames(logNamesToUse);
        sendAlertEvent.setSeverity(severity);
        sendAlertEvent.setStart(TimeUtils.getDateTimestamp(start));
        sendAlertEvent.setEnd(TimeUtils.getDateTimestamp(end));
        validateMsgRegex(msgRegex);
        sendAlertEvent.setMsgRegex(msgRegex);
        sendAlertEvent.setEventParameters(eventParameters);
        sendAlertEvent.setMaxCount(maxCount);
        sendAlertEvent.setForceAttachLogs(forceAttachLogs);

        // Persisting this operation
        Operation op = new Operation();
        op.setName("SEND ALERT " + eventId);
        op.setDescription("SEND ALERT EVENT code:" + eventId + ", severity:" + severity);
        op.setResourceType(ResourceOperationTypeEnum.SYS_EVENT);
        SysEvent sysEvent = createSysEventRecord(sysEventId, opID, op, force);

        // Starting send event job
        getExecutorServiceInstance().submit(sendAlertEvent);

        auditCallhome(OperationTypeEnum.SEND_ALERT,
                AuditLogManager.AUDITOP_BEGIN,
                null, nodeIds, logNames, start, end);

        return toTask(sysEvent, opID, op);
    }

    private TaskResourceRep toTask(DataObject resource, String taskId, Operation operation) {
        // If the Operation has been serialized in this request, then it should have the corresponding task embedded in it
        Task task = operation.getTask(resource.getId());
        if (task != null) {
            return toTask(task);
        }
        else {
            // It wasn't recently serialized, so fallback to looking for the task in the DB
            task = TaskUtils.findTaskForRequestId(dbClient, resource.getId(), taskId);
            if (task != null) {
                return toTask(task);
            }
            else {
                throw new IllegalStateException(String.format(
                        "Task not found for resource %s, op %s in either the operation or the database", resource.getId(), taskId));
            }
        }
    }

    private TaskResourceRep toTask(Task task) {
        TaskResourceRep taskResourceRep = new TaskResourceRep();

        taskResourceRep.setId(task.getId());
        NamedURI resource = task.getResource();
        NamedRelatedResourceRep namedRelatedResourceRep = new NamedRelatedResourceRep(resource.getURI(),
                new RestLinkRep("self", URI.create("/" + resource.getURI())), resource.getName());
        taskResourceRep.setResource(namedRelatedResourceRep);

        if (!StringUtils.isBlank(task.getRequestId())) {
            taskResourceRep.setOpId(task.getRequestId());
        }

        // Operation
        taskResourceRep.setState(task.getStatus());
        if (task.getServiceCode() != null) {
            taskResourceRep.setServiceError(toServiceErrorRestRep(toServiceCode(task.getServiceCode()),
                    task.getMessage()));
        } else {
            taskResourceRep.setMessage(task.getMessage());
        }
        taskResourceRep.setDescription(task.getDescription());
        taskResourceRep.setStartTime(task.getStartTime());
        taskResourceRep.setEndTime(task.getEndTime());
        taskResourceRep.setProgress(task.getProgress() != null ? task.getProgress() : 0);
        taskResourceRep.setQueuedStartTime(task.getQueuedStartTime());
        taskResourceRep.setQueueName(task.getQueueName());

        return taskResourceRep;
    }

    /**
     * Creates sysevent record after checking if there are any existing records.
     * If force is 1 will not check for existing records.
     */
    private synchronized SysEvent createSysEventRecord(URI sysEventId, String opID,
            Operation op, int force) {
        if (force != 1) {
            List sysEvents = dbClient.queryByType(SysEvent.class, true);
            if (sysEvents != null && sysEvents.iterator().hasNext()) {
                throw APIException.serviceUnavailable.sendEventBusy();
            }
        }

        _log.info("Event id is {} and operation id is {}", sysEventId, opID);

        SysEvent sysEvent = new SysEvent();
        sysEvent.setId(sysEventId);
        sysEvent.setLabel("System Event");
        dbClient.createObject(sysEvent);

        dbClient.createTaskOpStatus(SysEvent.class, sysEventId, opID, op);
        return sysEvent;
    }

    @Override
    public Response sendRegistrationEvent() {
        internalSendRegistrationEvent();

        return Response.ok().build();
    }

    void internalSendRegistrationEvent() {
        _callHomeEventManager.validateSendEvent();
        LicenseInfoListExt licenseList = null;
        try {
            licenseList = _licenseManager.getLicenseInfoListFromCoordinator();
        } catch (Exception e) {
            throw APIException.internalServerErrors.licenseInfoNotFoundForType("all license types");
        }
        if (licenseList != null) {
            // send registration events for each registered license type
            for (LicenseInfoExt licenseInfo : licenseList.getLicenseList()) {
                if (licenseInfo.isTrialLicense()) {
                    _log.warn("Cannot send regisration event to SYR for trial license {}",
                            licenseInfo.getLicenseType().toString());
                    throw APIException.forbidden.permissionDeniedForTrialLicense(
                            licenseInfo.getLicenseType().toString());
                }
                _callHomeEventsFacade.sendRegistrationEvent(licenseInfo, getMediaType());
            }
        }
        auditCallhome(OperationTypeEnum.SEND_REGISTRATION,
                AuditLogManager.AUDITLOG_SUCCESS, null);
    }

    @Override
    public Response sendHeartbeatEvent() {

        // if not configured for callhome, do not continue.
        _callHomeEventManager.validateSendEvent();

        LicenseInfoListExt licenseList = null;
        try {
            licenseList = _licenseManager.getLicenseInfoListFromCoordinator();
        } catch (Exception e) {
            throw APIException.internalServerErrors.licenseInfoNotFoundForType("all license types");
        }
        if (licenseList != null) {
            // send heart beat events for each registered license type
            for (LicenseInfoExt licenseInfo : licenseList.getLicenseList()) {
                if (licenseInfo.isTrialLicense()) {
                    _log.warn("Cannot send heartbeat event to SYR for trial license {} ",
                            licenseInfo.getLicenseType().toString());
                    throw APIException.forbidden.permissionDeniedForTrialLicense(
                            licenseInfo.getLicenseType().toString());
                }

                _callHomeEventsFacade.sendHeartBeatEvent(licenseInfo, getMediaType());
            }
        }

        auditCallhome(OperationTypeEnum.SEND_HEARTBEAT,
                AuditLogManager.AUDITLOG_SUCCESS, null);

        return Response.ok().build();
    }

    @Override
    public Response getNodeDataForEsrs() {

        try {
            auditCallhome(OperationTypeEnum.CREATE_ESRS_CONFIGURATION,
                    AuditLogManager.AUDITLOG_SUCCESS, null);
            return Response.status(200).entity(_buildEsrsDevice.build()).build();
        } catch (Exception e) {
            throw APIException.badRequests.getNodeDataForESRSFailure(e);
        }
    }

    /**
     * Spring Injected BuildEsrsConfigurationFile
     */
    @Autowired
    public void setCallHomeEventsFacade(CallHomeEventsFacade callHomeEventsFacade) {
        this._callHomeEventsFacade = callHomeEventsFacade;
    }

    /**
     *
     */
    @Autowired
    public void setBuildEsrsDevice(BuildEsrsDevice buildEsrsDevice) {
        _buildEsrsDevice = buildEsrsDevice;
    }

    /**
     * Record audit log for callhome service
     *
     * @param auditType Type of AuditLog
     * @param operationalStatus Status of operation
     * @param description Description for the AuditLog
     * @param descparams Description paramters
     */
    public void auditCallhome(OperationTypeEnum auditType,
            String operationalStatus,
            String description,
            Object... descparams) {

        _auditMgr.recordAuditLog(null, null,
                EVENT_SERVICE_TYPE,
                auditType,
                System.currentTimeMillis(),
                operationalStatus,
                description,
                descparams);
    }

    /**
     * Set the CallHomeEventManager
     */
    @Autowired
    public void setLicenseManager(LicenseManager licenseManager) {
        _licenseManager = licenseManager;
    }

    /**
     * Set the CallHomeEventManager
     */
    @Autowired
    public void setCallHomeEventManager(CallHomeEventManager callHomeEventManager) {
        _callHomeEventManager = callHomeEventManager;
    }
}

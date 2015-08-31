/*
 * Copyright (c) 2013-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.resource;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.CoordinatorClient.LicenseType;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.SysEvent;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.event.EventParameters;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ForbiddenException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.resources.ServiceErrorFactory;
import com.emc.storageos.systemservices.impl.eventhandler.connectemc.BuildEsrsDevice;
import com.emc.storageos.systemservices.impl.eventhandler.connectemc.CallHomeConstants;
import com.emc.storageos.systemservices.impl.eventhandler.connectemc.CallHomeEventManager;
import com.emc.storageos.systemservices.impl.eventhandler.connectemc.CallHomeEventsFacade;
import com.emc.storageos.systemservices.impl.eventhandler.connectemc.SendAlertEvent;
import com.emc.storageos.systemservices.impl.licensing.LicenseInfoExt;
import com.emc.storageos.systemservices.impl.licensing.LicenseInfoListExt;
import com.emc.storageos.systemservices.impl.licensing.LicenseManager;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.vipr.model.sys.SysSvcTask;
import com.emc.vipr.model.sys.SysSvcTaskList;

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
    public SysSvcTask sendInternalAlert(String source, int eventId, List<String> nodeIds, List<String> nodeNames, List<String> logNames,
                                        int severity, String start, String end, String msgRegex, int maxCount,
                                        EventParameters eventParameters) throws Exception {
        _log.info("Sending internal alert for id: {} and source: {}", eventId, source);
        return sendAlert(source, eventId, nodeIds, nodeNames, logNames, severity, start, end
                , msgRegex, maxCount, true, 1, eventParameters);
    }

    @Override
    public SysSvcTask sendAlert(String source, int eventId, List<String> nodeIds, List<String> nodeNames, List<String> logNames, int severity,
                                String start, String end, String msgRegex, int maxCount, boolean forceAttachLogs,
                                int force, EventParameters eventParameters) throws Exception {
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
        sendAlertEvent.setStart(getDateTimestamp(start));
        sendAlertEvent.setEnd(getDateTimestamp(end));
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
        createSysEventRecord(sysEventId, opID, op, force);

        // Starting send event job
        getExecutorServiceInstance().submit(sendAlertEvent);

        auditCallhome(OperationTypeEnum.SEND_ALERT,
                AuditLogManager.AUDITOP_BEGIN,
                null, nodeIds, logNames, start, end);

        return toSysSvcTask(sysEventId, opID, op);
    }

    /**
     * Creates sysevent record after checking if there are any existing records.
     * If force is 1 will not check for existing records.
     */
    private synchronized void createSysEventRecord(URI sysEventId, String opID,
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
    }

    /**
     * Creates and returns syssvc task for the passed information.
     */
    private SysSvcTask toSysSvcTask(URI sysEventId, String opId,
            Operation operation) {
        SysSvcTask sysSvcTask = new SysSvcTask();
        sysSvcTask.setOpId(opId);

        // Setting resource
        NamedRelatedResourceRep resource = new NamedRelatedResourceRep();
        resource.setId(sysEventId);
        sysSvcTask.setResource(resource);

        sysSvcTask.setState(operation.getStatus());
        sysSvcTask.setDescription(operation.getDescription());
        sysSvcTask.setStartTime(operation.getStartTime());
        sysSvcTask.setEndTime(operation.getEndTime());

        // Setting message
        if (operation.getServiceCode() != null) {
            sysSvcTask.setServiceError(ServiceErrorFactory.toServiceErrorRestRep(
                    ServiceError.buildServiceError(ServiceCode.toServiceCode(operation
                            .getServiceCode()), operation.getMessage())));
        } else {
            sysSvcTask.setMessage(operation.getMessage());
        }
        return sysSvcTask;
    }

    @Override
    public SysSvcTaskList getTasks(URI id) {

        // test validity of alert event id(id)
        if (!URIUtil.isValid(id)) {
            // this won't print id if invalid to avoid XSS issues
            throw APIException.badRequests.invalidURI("alert event id");
        }

        SysEvent sysEvent = permissionsHelper.getObjectById(id, SysEvent.class);
        if (sysEvent == null) {
            throw APIException.badRequests.parameterIsNotValid(id.toString());
        }
        SysSvcTaskList tasks = new SysSvcTaskList();
        OpStatusMap opStatusMap = sysEvent.getOpStatus();
        for (Map.Entry<String, Operation> entry : opStatusMap.entrySet()) {
            tasks.addTask(toSysSvcTask(id, entry.getKey(), entry.getValue()));
        }
        return tasks;
    }

    @Override
    public SysSvcTask getTask(URI id, String opId) {

        // test validity of alert event id(id)
        if (!URIUtil.isValid(id)) {
            // this won't print id if invalid to avoid XSS issues
            throw APIException.badRequests.parameterIsNotValid("alert event id");
        }
        // test validity of task id (opId)
        if (!opId.matches("(?i)([A-F0-9]{8}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{12})")) {
            // this won't print id if invalid to avoid XSS issues
            throw APIException.badRequests.parameterIsNotValid("task id");
        }

        SysEvent sysEvent = permissionsHelper.getObjectById(id, SysEvent.class);
        if (sysEvent == null) {
            throw APIException.badRequests.parameterIsNotValid(id.toString());
        }

        Operation op = sysEvent.getOpStatus().get(opId);
        if (op == null) {
            throw APIException.badRequests.parameterIsNotValid(opId);
        }
        return toSysSvcTask(id, opId, op);
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

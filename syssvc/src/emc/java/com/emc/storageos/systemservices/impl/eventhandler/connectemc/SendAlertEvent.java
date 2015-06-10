/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.eventhandler.connectemc;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.transform.stream.StreamResult;

import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.SysEvent;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.services.util.AlertsLogger;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.licensing.LicenseInfoExt;
import com.emc.storageos.systemservices.impl.logsvc.merger.LogNetworkStreamMerger;
import com.emc.storageos.systemservices.impl.logsvc.LogSvcPropertiesLoader;
import com.emc.storageos.systemservices.impl.resource.LogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.ema.EmaApi;
import com.emc.ema.EmaApiEventType;
import com.emc.ema.EmaApiIdentifierType;
import com.emc.ema.EmaException;
import com.emc.ema.event.objects.EventType;
import com.emc.storageos.model.event.EventParameters;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.vipr.model.sys.logging.LogRequest;

import static com.emc.storageos.systemservices.impl.eventhandler.connectemc
        .CallHomeConstants.*;

/**
 * Class that builds alert event with all its attachments.
 */
public class SendAlertEvent extends SendEvent implements Runnable{
    private static final Logger _log = LoggerFactory
            .getLogger(SendAlertEvent.class);
    private int _eventId;
    private List<String> _logNames;
    private int _severity;
    private Date _start;
    private Date _end;
    private List<String> _nodeIds;
    private String _msgRegex;
    private int maxCount;
    private EventParameters _eventParameters;
    private final static List<String> sysEventlogFileNames = new ArrayList<String>() {{
        add("systemevents");
    }};
    private URI sysEventId;
    private String operationId;
    protected DbClient dbClient;
    private BasePermissionsHelper permissionsHelper;
    private static final AlertsLogger alertsLog = AlertsLogger.getAlertsLogger();

    public SendAlertEvent(ServiceImpl service, DbClient dbClient,
                     LogSvcPropertiesLoader logSvcPropertiesLoader, URI sysEventId,
                     String operationId, MediaType mediaType,
                     LicenseInfoExt licenseInfo, BasePermissionsHelper permissionsHelper,
                     CoordinatorClientExt coordinator) {
        super(service, logSvcPropertiesLoader, mediaType,
                licenseInfo, coordinator);
        this.dbClient = dbClient;
        this.sysEventId = sysEventId;
        this.operationId = operationId;
        this.permissionsHelper = permissionsHelper;
    }

    @Override
    public void run() {
        try{
            super.callEMCHome();
            dbClient.ready(SysEvent.class, sysEventId, operationId);
            alertsLog.warn("Alert event initiated by "+ _eventParameters.getContact()+" is sent successfully");
        }
        catch (APIException api) {
            dbClient.error(SysEvent.class, sysEventId, operationId, api);
            alertsLog.error("Failed to send alert event initiated by "+
                    _eventParameters.getContact() + ". "+ api.getMessage());
        }
        finally {
            SysEvent sysEvent = permissionsHelper.getObjectById(sysEventId, SysEvent.class);
            dbClient.removeObject(sysEvent);
        }
    }

    /**
     * Generate the attachments for alerts event
     * 1. ovf properties file
     * 2. log file for the parameters given
     * 3. system events file for the parameters given
     */
    @Override
    protected ArrayList<String> genAttachFiles() throws Exception {

        _log.info("Start SendAlertEvent::genAttachFiles");
        // Generate message file
        ArrayList<String> fileList = new ArrayList<String>();

        // Generate log file
        ZipOutputStream outputStream = null;
        try {
            fileList.add(generateConfigFile());
            fileList.add(generateSystemEventFile());

            // Create the log request info bean from the request data.
            String fileName = SYSTEM_LOGS_FILE_NAME + getFileExtension();
            _log.debug("Logs zip entry name {}", fileName);
            LogRequest logReqInfo = new LogRequest.Builder().nodeIds(_nodeIds)
                    .baseNames(_logNames).logLevel(_severity).startTime(_start)
                    .endTime(_end).regex(_msgRegex).maxCont(maxCount).build();

            generateLogFile(logReqInfo, mediaType, SYSTEM_LOGS_FILE_PATH,
                    fileName);
            fileList.add(SYSTEM_LOGS_FILE_PATH);

        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }

        _log.info("Finish SendAlertEvent::genAttachFiles");
        return fileList;
    }

    /**
     * Gets system events for the parameters entered,
     * loads the output to file with name SYSTEM_EVENT_FILE_NAME and returns it.
     */
    private String generateSystemEventFile() throws IOException {
        // Create the log request info bean from the request data.
        LogRequest logReqInfo = new LogRequest.Builder().nodeIds(_nodeIds)
                .baseNames(sysEventlogFileNames).logLevel(_severity).startTime(_start)
                .endTime(_end).regex(_msgRegex).maxCont(maxCount).build();
        String fileName = SYSTEM_EVENT_FILE_NAME + getFileExtension();
        generateLogFile(logReqInfo, mediaType, SYSTEM_EVENT_FILE_PATH,
                fileName);
        return SYSTEM_EVENT_FILE_PATH;
    }

    /**
     * Gets logs with request info provided and populates them to the file path
     * specified, in zip format with fileName as zip entry.
     */
    private synchronized void generateLogFile(LogRequest logReqInfo, MediaType mediaType,
                                 String filePath, String fileName) throws IOException{
        ZipOutputStream outputStream = null;
        try {
            _log.info("Populating logs to file: {} start", filePath);
            logReqInfo.setMaxBytes(getAttachmentsMaxSizeMB()*
                    BYTE_TO_MB*logSvcPropertiesLoader.getZipFactor());
            final LogNetworkStreamMerger logRequestMgr = new LogNetworkStreamMerger
                    (logReqInfo, mediaType, logSvcPropertiesLoader);
            outputStream = new ZipOutputStream(new FileOutputStream(filePath));
            StreamingOutput responseStream = new StreamingOutput() {
                @Override
                public void write(OutputStream outputStream) {
                    try {
                        LogService.runningRequests.incrementAndGet();
                        logRequestMgr.streamLogs(outputStream);
                        _log.info("Total streamed bytes: {}", logRequestMgr.streamedBytes);
                    } finally {
                        LogService.runningRequests.decrementAndGet();
                    }
                }
            };
            ZipEntry ze= new ZipEntry(fileName);
            outputStream.putNextEntry(ze);
            responseStream.write(outputStream);
            _log.info("Populating logs to file: {} end", filePath);
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    protected EventType getEventType() throws EmaException, JAXBException {
        EventType event = new EventType();
        event.setSymptomCode(getEventId() > 0 ? Integer.toString(getEventId()) :
                SYMPTOM_CODE_REQUEST_LOGS);
        event.setDescription(SEND_ALERT_DESCRIPTION);
        event.setCategory(EmaApiEventType.EMA_EVENT_CATEGORY_UNKNOWN);
        event.setSeverity(EmaApiEventType.EMA_EVENT_SEVERITY_DEBUG_STR);
        event.setStatus(EmaApiEventType.EMA_EVENT_STATUS_UNKNOWN);
        event.setCallHome(callHome);
        event.setEventData(buildEventData());
        return event;
    }

    /**
     * build events data as marshalled string of xml data.
     *
     * @return
     */
    private String buildEventData() throws JAXBException {

        if (_eventParameters != null) {
            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(EventParameters.class);
                Marshaller marshaller = jaxbContext.createMarshaller();
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
                Writer outWriter = new StringWriter();
                StreamResult result = new StreamResult(outWriter);
                marshaller.marshal(_eventParameters, result);
                return outWriter.toString();
            } catch (JAXBException e) {
                _log.error("Failed to generate user message file. "
                        + e.getMessage());
                throw e;
            }
        } else {
            return null;
        }
    }

    /**
     * Set specific embed level for this event in CallHome Identifier section.
     */
    @Override
    protected void overrideIdentifierData(EmaApiIdentifierType identifier) throws
            EmaException {
        identifier.setEmbedLevel(EmaApi.EMA_EMBED_LEVEL_EXTERNAL_STR);
    }

    public int getEventId() {
        return _eventId;
    }

    public void setEventId(int eventId) {
        _eventId = eventId;
    }

    public void setLogNames(List<String> logNames) {
        _logNames = logNames;
    }

    public void setSeverity(int severity) {
        _severity = severity;
    }

    public void setStart(Date start) {
        _start = start;
    }

    public void setEnd(Date end) {
        _end = end;
    }

    public void setNodeIds(List<String> nodeIds) {
        _nodeIds = nodeIds;
    }

    public void setMsgRegex(String msgRegex) {
        _msgRegex = msgRegex;
    }

    public void setEventParameters(EventParameters eventParameters) {
        _eventParameters = eventParameters;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }
}
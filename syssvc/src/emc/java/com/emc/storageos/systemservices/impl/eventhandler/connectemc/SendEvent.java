/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.eventhandler.connectemc;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import com.emc.ema.*;
import com.emc.ema.event.objects.EventType;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.management.jmx.logging.IdentifierManager;
import com.emc.storageos.services.util.AlertsLogger;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.systemservices.exceptions.LocalRepositoryException;
import com.emc.storageos.systemservices.impl.logsvc.LogSvcPropertiesLoader;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.ema.event.objects.ConnectionType;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.systemservices.impl.licensing.LicenseInfoExt;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;

import static com.emc.storageos.systemservices.impl.eventhandler.connectemc
        .CallHomeConstants.*;

public abstract class SendEvent {

    private static final Logger _log = LoggerFactory.getLogger(SendEvent.class);
    protected String _fileId;
    protected IdentifierManager _identifierManager;
    private String _networkIpAddress;
    protected LicenseInfoExt licenseInfo;
    protected MediaType mediaType;
    protected LogSvcPropertiesLoader logSvcPropertiesLoader;
    protected CoordinatorClientExt coordinator;
    
    private boolean forceAttachLogs = false;
    
    public SendEvent(ServiceImpl service, LogSvcPropertiesLoader logSvcPropertiesLoader,
                     MediaType mediaType, LicenseInfoExt licenseInfo,
                     CoordinatorClientExt coordinator) {
        URI endpointUri = coordinator.getNodeEndpoint(service.getEndpoint().getHost());
        _networkIpAddress = (endpointUri != null) ? coordinator.getIPAddrFromUri(endpointUri)
                : service.getEndpoint().getHost();
        _log.info("_networkIpAddress: {}", _networkIpAddress);
        _identifierManager = IdentifierManager.getInstance();
        this.logSvcPropertiesLoader = logSvcPropertiesLoader;
        this.mediaType = mediaType;
        this.licenseInfo = licenseInfo;
        this.coordinator = coordinator;
    }

    /**
     * Call EMC connect service API to generate event files
     */
    public void callEMCHome() {
        try {
            if (licenseInfo == null) {
                _log.warn("License information cannot be null. Returning without sending event.");
                return;
            }
            synchronized (SendEvent.class) {
                _log.info("callEMCHome(): start ");
                _fileId = licenseInfo.getProductId();
                // Construct main connect home object that holds the Alert file.
                EmaApiConnectHome alertFile = new EmaApiConnectHome();
                // Setup the logging information
                EmaApiLogType log = new EmaApiLogType();
                log.setLogToDirectory(LOG_PATH);
                log.setLogToFilename(LOG_FILE_NAME + _fileId + ".log");
                // build the common identifier section.
                buildIdentifierSection(alertFile);
                // build the common identifier section.
                buildConnectionSection(alertFile);
                // build the event section
                buildAlertFile(alertFile, log);
                _log.info("callEMCHome(): end ");
            }
        } catch (APIException api) {
            throw api;
        } catch (Exception e) {
            _log.error("Error occurred while sending event. {}", e);
            throw APIException.internalServerErrors.sendEventError(e.getMessage());
        }
    }

    /**
     * Builds the Identifier element of ConnectHome
     */
    private void buildIdentifierSection(EmaApiConnectHome alertFile) throws
            EmaException {
        EmaApiIdentifierType identifier = new EmaApiIdentifierType();
        EmaApiNode node = alertFile.getNode();
        identifier.setClarifyID(licenseInfo.getProductId());
        identifier.setDeviceType(_identifierManager.findDeviceType());
        identifier.setDeviceState(_identifierManager.findDeviceState());
        identifier.setModel(licenseInfo.getModelId());
        identifier.setOS(_identifierManager.findOS());
        identifier.setOSVER(_identifierManager.findOSVersion());
        identifier.setVendor("EMC");
        identifier.setSiteName("");
        identifier.setSerialNumber(licenseInfo.getProductId());
        identifier.setUcodeVer(_identifierManager.findPlatform());
        identifier.setWWN(licenseInfo.getLicenseTypeIndicator());
        node.setIdentifier(identifier);
        overrideIdentifierData(identifier);
    }

    /**
     * Builds the Connection element of ConnectHome
     *
     * @param alertFile
     */
    private void buildConnectionSection(EmaApiConnectHome alertFile) throws EmaException {
        ConnectionType connectionType = alertFile.getNode().getConnection();
        connectionType.setConnectType(CONNECTION_TYPE_ESRS);
        connectionType.setAppName(PRODUCT_NAME);
        connectionType.setPort(SECURED_CONNECTION_PORT);
        connectionType.setIPAddress(_networkIpAddress);
    }

    /**
     * Get file name from full path
     *
     * @param fileName
     * @return file name
     */
    protected String getTargetFileName(String fileName) {
        return fileName.substring(fileName.lastIndexOf("/") + 1);
    }

    /**
     * Get attach files' list
     *
     * @return list of file names
     */
    protected abstract ArrayList<String> genAttachFiles() throws Exception;

    /**
     * Builds alert file with all required information - type of event,
     * attachments and sends to ConnectEMC
     */
    protected void buildAlertFile(EmaApiConnectHome alertFile,
                                  EmaApiLogType log) throws Exception {
        _log.info("Start SendEvent::buildEventType");
        alertFile.eventAdd(getEventType(), log);

        // Create event file to attach
        String eventFilename = CONNECT_EMC_HOME + EmaApiUtils.emaGenerateFilename
                (_fileId);
        _log.info("Event filename: {}", eventFilename);
        ArrayList<String> fileList = genAttachFiles();
        BadRequestException badRequestException = null;
        
        if (fileList != null && !fileList.isEmpty()) {
            boolean attachLogs = true;
            try {
                validateAttachmentSize(fileList);
            } catch(BadRequestException e) {
                if(forceAttachLogs) {
                    throw e;
                }
                badRequestException = e;
                attachLogs = false;
            }

            ArrayList<EmaApiFilenameType> attachFiles = new
                    ArrayList<EmaApiFilenameType>();
            
            if(attachLogs) {                                       
                for (String file : fileList) {
                    EmaApiFilenameType filename = new EmaApiFilenameType();
                    filename.setQualifiedFileName(file);
                    filename.setTargetFileName(getTargetFileName(file));
                    attachFiles.add(filename);
                }
            }
            else { // log size too big, not to attach logs                
                for (String file : fileList) {                    
                    if (file.equals(SYSTEM_LOGS_FILE_PATH) || file.equals
                            (SYSTEM_EVENT_FILE_PATH)) {
                        continue;
                    }
                    EmaApiFilenameType filename = new EmaApiFilenameType();
                    filename.setQualifiedFileName(file);
                    filename.setTargetFileName(getTargetFileName(file));
                    attachFiles.add(filename);
                }
                AlertsLogger.getAlertsLogger().warn(
                        "ConnectEMC alert will be sent without logs attached due to logs have exceeded max allowed size ("
                        + this.getAttachmentsMaxSizeMB() + " MB)");
            }
            alertFile.addFileRawData(eventFilename, attachFiles, log);
        }
        alertFile.write(eventFilename, log);
        alertFile.emaCreateDotEndFile(eventFilename, log);
        _log.info("Finish SendEvent::buildEventType");
        if(badRequestException != null){
            throw badRequestException;
        }
    }

    /**
     * Constructs alert object using EventType class.
     */
    protected abstract EventType getEventType() throws Exception;

    /**
     * Constructs ArrayList of query parameters used
     */
    protected ArrayList<String> collectQueryParameters(){
        return new ArrayList<String>();
    }

    /**
     * Required to set the appropriate embed level in the identifier section of
     * the CallHome event.
     */
    protected abstract void overrideIdentifierData(EmaApiIdentifierType identifier)
            throws EmaException;


    /**
     * Generates configuration file with name taken from variable CONFIG_FILE_NAME
     * Returns the created file name.
     */
    protected String generateConfigFile()
            throws JAXBException, LocalRepositoryException, IOException {
        ZipOutputStream zos = null;
        try {
            PropertyInfoExt properties = new PropertyInfoExt(coordinator.getPropertyInfo().getProperties());
            zos = new ZipOutputStream(new FileOutputStream
                    (CONFIG_FILE_PATH));
            ZipEntry ze = new ZipEntry(CONFIG_FILE_NAME + getFileExtension());
            zos.putNextEntry(ze);

            if (MediaType.APPLICATION_JSON_TYPE.equals(mediaType)) {                
                (new ObjectMapper()).writeValue(zos, properties); // gson should not be used any more
            } else {
                JAXBContext jaxbContext = JAXBContext.newInstance(PropertyInfo.class);
                Marshaller marshaller = jaxbContext.createMarshaller();
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
                marshaller.marshal(properties, zos);
            }
            zos.flush();
        } finally {
            if (zos != null) {
                zos.close();
            }
        }

        return CONFIG_FILE_PATH;
    }

    /**
     * Returns file extension based on media type. By default it returns .xml.
     */
    protected String getFileExtension() {
        _log.debug("Media type requested is {}", mediaType);
        if (MediaType.APPLICATION_JSON_TYPE.equals(mediaType)) {
            return ".json";
        }
        return ".xml";
    }

    /**
     * Validates attachment size against maximum allowed of 16MB.
     * Throws error if size is more than allowed.
     */
    public void validateAttachmentSize(List<String> filePathNames) {
        int attachmentsMaxMB = getAttachmentsMaxSizeMB();
        if (attachmentsMaxMB == 0 || filePathNames == null ||
                filePathNames.isEmpty()) {
            return;
        }

        double attachmentSizeBytes = 0;
        double totalLogsSize = 0;
        for (String path : filePathNames) {
            double size = new File(path).length();
            if (size > 0) {
                if (path.equals(SYSTEM_LOGS_FILE_PATH) || path.equals
                        (SYSTEM_EVENT_FILE_PATH)) {
                    totalLogsSize += size;
                }

                _log.info("Attachment {} size is {} MB", path, (size / BYTE_TO_MB));
                attachmentSizeBytes += size;
            }
        }

        if (attachmentSizeBytes > 0) {
            long attachmentSizeMB = (long) Math.ceil(attachmentSizeBytes / BYTE_TO_MB);
            if (attachmentSizeMB > attachmentsMaxMB) {
                _log.error("Attachments size {} MB is more than allowed max {} MB ",
                        attachmentSizeMB, attachmentsMaxMB);
                if (totalLogsSize > 0) {
                    totalLogsSize = Math.ceil(totalLogsSize / BYTE_TO_MB);
                    _log.info("Logs attachment size is {} MB ", totalLogsSize);
                    throw APIException.badRequests.attachmentLogsSizeError
                            (attachmentSizeMB,
                            (long) totalLogsSize, attachmentsMaxMB,
                                    collectQueryParameters().toString());
                }
                throw APIException.internalServerErrors.attachmentSizeError
                        (attachmentSizeMB,
                        attachmentsMaxMB);
            }
        }
    }

    /**
     * Returns maximum size allowed for attachments.
     * Gets the size from properties.
     */
    protected int getAttachmentsMaxSizeMB() {
        return logSvcPropertiesLoader.getAttachmentMaxSizeMB();
    }

    public boolean isForceAttachLogs() {
        return forceAttachLogs;
    }

    public void setForceAttachLogs(boolean forceAttachLogs) {
        this.forceAttachLogs = forceAttachLogs;
    }
}

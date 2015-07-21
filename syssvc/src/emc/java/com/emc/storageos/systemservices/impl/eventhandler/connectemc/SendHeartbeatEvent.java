/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.eventhandler.connectemc;

import static com.emc.storageos.systemservices.impl.eventhandler.connectemc.CallHomeConstants.HEARTBEAT_DESCRIPTION;
import static com.emc.storageos.systemservices.impl.eventhandler.connectemc.CallHomeConstants.SYMPTOM_CODE_HEARTBEAT;
import static com.emc.storageos.systemservices.impl.eventhandler.connectemc.CallHomeConstants.callHome;


import java.util.ArrayList;

import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.systemservices.impl.licensing.LicenseInfoExt;
import com.emc.storageos.systemservices.impl.logsvc.LogSvcPropertiesLoader;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.ema.EmaApi;
import com.emc.ema.EmaApiEventType;
import com.emc.ema.EmaApiIdentifierType;
import com.emc.ema.EmaException;
import com.emc.ema.event.objects.EventType;

import javax.ws.rs.core.MediaType;

public class SendHeartbeatEvent extends SendEvent {
    private static final Logger _log = LoggerFactory.getLogger(SendHeartbeatEvent.class);

    public SendHeartbeatEvent(ServiceImpl service, LogSvcPropertiesLoader logSvcPropertiesLoader,
                                 MediaType mediaType, LicenseInfoExt licenseInfo,
                                 CoordinatorClientExt coordinator) {
        super(service, logSvcPropertiesLoader, mediaType, licenseInfo, coordinator);
    }

    /**
     * Generate the attached file list.
     */
    @Override
    protected ArrayList<String> genAttachFiles() {

        _log.info("Start SendHeartbeatEvent::genAttachFiles");
        ArrayList<String> fileList = new ArrayList<String>();
        try{
            fileList.add(generateConfigFile());
        } catch (Exception e){
            _log.error("Error occurred while creating config file. {}", e);
        }
        _log.info("End SendHeartbeatEvent::genAttachFiles");
        return fileList;

    }

    protected EventType getEventType() throws EmaException {
        EventType event = new EventType();
        event.setSymptomCode(SYMPTOM_CODE_HEARTBEAT);
        event.setDescription(HEARTBEAT_DESCRIPTION);
        event.setCategory(EmaApiEventType.EMA_EVENT_CATEGORY_STATUS);
        event.setSeverity(EmaApiEventType.EMA_EVENT_SEVERITY_MARK_STR);
        event.setStatus(EmaApiEventType.EMA_EVENT_STATUS_UNKNOWN);
        event.setCallHome(callHome);
        event.setEventData("Licensed Storage Capacity: " + licenseInfo.getStorageCapacity());
        return event;
    }

    /**
     * Set specific embed level for this event in CallHome Identifier section.
     */
    @Override
    protected void overrideIdentifierData(EmaApiIdentifierType identifier)
            throws EmaException {
        identifier.setEmbedLevel(EmaApi.EMA_EMBED_LEVEL_EXTERNAL_STR);
    }
}

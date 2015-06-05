/**
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

import static com.emc.storageos.systemservices.impl.eventhandler.connectemc.CallHomeConstants.CAPACITY_EXCEEDED_DESCRIPTION;
import static com.emc.storageos.systemservices.impl.eventhandler.connectemc.CallHomeConstants.SYMPTOM_CODE_CAPACITY_EXCEEDED;
import static com.emc.storageos.systemservices.impl.eventhandler.connectemc.CallHomeConstants.callHome;
import java.util.ArrayList;

import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.systemservices.impl.licensing.LicenseInfoExt;
import com.emc.storageos.systemservices.impl.logsvc.LogSvcPropertiesLoader;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.ema.EmaApiEventType;
import com.emc.ema.EmaApiIdentifierType;
import com.emc.ema.EmaException;
import com.emc.ema.event.objects.EventType;

import javax.ws.rs.core.MediaType;

public class SendCapacityExceededEvent extends SendEvent {
    private static final Logger _log = LoggerFactory.getLogger(SendCapacityExceededEvent.class);

    public SendCapacityExceededEvent(ServiceImpl service, LogSvcPropertiesLoader logSvcPropertiesLoader,
                               MediaType mediaType, LicenseInfoExt licenseInfo,
                               CoordinatorClientExt coordinator) {
        super(service, logSvcPropertiesLoader, mediaType, licenseInfo, coordinator);
    }

    protected ArrayList<String> genAttachFiles() throws Exception {
        return null;
    }

    protected EventType getEventType() throws EmaException {
        EventType event = new EventType();
        event.setSymptomCode(SYMPTOM_CODE_CAPACITY_EXCEEDED);
        event.setDescription(CAPACITY_EXCEEDED_DESCRIPTION);
        event.setCategory(EmaApiEventType.EMA_EVENT_CATEGORY_STATUS);
        event.setSeverity(EmaApiEventType.EMA_EVENT_SEVERITY_MARK_STR);
        event.setStatus(EmaApiEventType.EMA_EVENT_STATUS_UNKNOWN);
        event.setCallHome(callHome);
        return event;
    }

    /**
     * Set specific embed level for this event in CallHome Identifier section.
     */
    @Override
    protected void overrideIdentifierData(EmaApiIdentifierType identifier) throws EmaException {

    }
}
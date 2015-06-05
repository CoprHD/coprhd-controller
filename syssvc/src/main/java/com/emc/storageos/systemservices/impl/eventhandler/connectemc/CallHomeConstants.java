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

import javax.xml.datatype.XMLGregorianCalendar;

import org.slf4j.helpers.MessageFormatter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public interface CallHomeConstants {
    public static final String BASE_URL_FORMAT = "http://{}:{}";
    public static final String CALL_URL = MessageFormatter.arrayFormat(
    BASE_URL_FORMAT, new Object[] { "localhost", "9998" }).getMessage();
    // connectemc polling directory. Data is this directory will be send to SYR.
    public static final String CONNECT_EMC_HOME = "/data/connectemc/poll/";
    // File name and location containing alert information to SYR.
    public static final String SYSTEM_LOGS_FILE_NAME = "logs";
    public static final String SYSTEM_LOGS_FILE_PATH =
            "/data/connectemc/tmp/logs.zip";
    public static final String SYSTEM_EVENT_FILE_NAME =  "system-events";
    public static final String SYSTEM_EVENT_FILE_PATH =
            "/data/connectemc/tmp/system-events.zip";
    // File name and location containing config properties data information to SYR.
    public static final String CONFIG_FILE_NAME = "config-properties";
    public static final String CONFIG_FILE_PATH =
            "/data/connectemc/tmp/config-properties.zip";
    // File name and location containing user generated message as part of system alert.
    public static final String USER_MSG_FILE_NAME = "/data/connectemc/tmp/MessageFile.xml";
    public static final String subComponent = "";
    public static final String description = "";
    public static final String eventData = "";
    public static final String callHome = "true";
    public static final XMLGregorianCalendar firstTime = null;
    public static final XMLGregorianCalendar lastTime = null;
    public static final int count = 0;
    // Temporary location for connectemc responses before being moved to poll.
    public static final String LOG_PATH = "/data/connectemc/tmp/";
    public static final String LOG_FILE_NAME = "JavaConnectEMC";
    // ESRS connection type and port.
    public static final String CONNECTION_TYPE_ESRS = "ESRS";
    public static final String SECURED_CONNECTION_PORT = "22";
    // Symptom code and description for system registration event in SYR.
    public static final String SYMPTOM_CODE_REGISTRATION = "100";
    public static final String REGISTRATION_DESCRIPTION = "Registration Event";
    // Symptom code and description for heartbeat event in SYR.
    public static final String SYMPTOM_CODE_HEARTBEAT = "101";
    public static final String HEARTBEAT_DESCRIPTION = "Heartbeat Event";
    // Symptom code and description for capacity exceeded event in SYR.
    public static final String SYMPTOM_CODE_CAPACITY_EXCEEDED = "997";
    public static final String CAPACITY_EXCEEDED_DESCRIPTION = "Storage Capacity Exceeded Event";
    // Symptom code and description for license expiration event in SYR.
    public static final String SYMPTOM_CODE_EXPIRATION = "998";
    public static final String EXPIRATION_DESCRIPTION = "Expiration Event";
    // Symptom code and description for alert event in SYR.
    public static final String SYMPTOM_CODE_REQUEST_LOGS = "999";
    public static final String SEND_ALERT_DESCRIPTION = "Customer Generated Alert Event";
    public static final Set<Integer> VALID_ALERT_EVENT_IDS =
            Collections.unmodifiableSet(new HashSet<Integer>() {{
                add(999);
                add(599);
            }});
    // product name in SYR.
    public static final String PRODUCT_NAME = "ViPR";
    // Family Name in SYR,.
    public static final String FAMILY_TYPE_NAME = "Storage Management";
    // run send event scheduler 15 seconds after server starts
    public static final int SERVICE_START_LAG = 120;
    // run send event scheduler every 24 hours. Measured in seconds.
    public static final int LAG_BETWEEN_RUNS = 86400;
    // Number of day to elapse before sending heartbeat event to SYR.
    public static final int HEARTBEART_EVENT_THRESHOLD = 30;
    // Number of day to elapse before sending registration event to SYR.
    public static final int REGISTRATION_EVENT_THRESHOLD = 30;
    // Number of day to elapse before sending additional license expiration event to SYR.
    public static final int LICENSE_EXPIRATION_EVENT_THRESHOLD = 14;
    // Number of day to elapse before sending additional capacity exceeded event to SYR.
    public static final int CAPACITY_EXCEEDED_EVENT_THRESHOLD = 14;
    // Date format used for serializing dates in coordinator service for the callhome events.
    public static final String SERIALIZE_DATE_FORMAT = "MM/dd/yyyy";
    
    // The following are constants used for encoding/decoding data into coordinator service.
    public static final String CALL_HOME_INFO = "callHomeInfo";
    public static final String LAST_REGISTRATION_EVENT_DATE = "lastRegistrationEventDate";
    public static final String LAST_HEARBEAT_EVENT_DATE = "lastHeartbeatEventDate";
    public static final String LAST_EXPIRATION_EVENT_DATE = "lastExpirationEventDate";
    public static final String TARGET_PROPERTY = "callHomeInfoProperty";
    public static final String TARGET_PROPERTY_ID = "global";
    public static final String ENCODING_SEPARATOR = "\0";
    public static final String ENCODING_EQUAL = "=";
    public static final String ENCODING_INVALID = "";
    public static final String VALUE_NOT_SET = "NA";
    // for representing the standalone server.
    public static final String STANDALONE = "standalone";
    public static final long BYTE_TO_MB = 1048576;
    public static int LOCK_WAIT_TIME_MS = 5000;
    public static int MAX_LOCK_WAIT_TIME_MS = 3600000; //1hr
}

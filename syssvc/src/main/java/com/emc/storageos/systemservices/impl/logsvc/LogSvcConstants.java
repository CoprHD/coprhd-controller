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
package com.emc.storageos.systemservices.impl.logsvc;

import javax.ws.rs.core.MediaType;
import java.util.*;

public class LogSvcConstants {

    public static final int CAN_PARSE_COUNT = 5;
    public static final Map<String, String> logAliasNames;

    static {
        Map<String, String> tempMap = new HashMap<String, String>();
        tempMap.put("systemmessages", "messages");
        logAliasNames = Collections.unmodifiableMap(tempMap);
    }

    public static final Set<MediaType> ACCEPTED_MEDIA_TYPES =
            Collections.unmodifiableSet(new HashSet<MediaType>() {
                {
                    add(MediaType.APPLICATION_JSON_TYPE);
                    add(MediaType.APPLICATION_XML_TYPE);
                    add(MediaType.TEXT_PLAIN_TYPE);
                }
            });

    public static final String DEFAULT_CHARSET = "UTF-8";
    public static final String NEW_LINE = "\n";
    public static final byte[] NEW_LINE_BYTES = NEW_LINE.getBytes();

    public static final String MESSAGE = "message";
    public static final String NODE = "node";
    public static final String LINE_NO = "line";
    public static final String CLASS_NAME = "class";
    public static final String SERVICE_NAME = "service";
    public static final String THREAD = "thread";
    public static final String SEVERITY = "severity";
    public static final String TIME = "time";
    public static final String TIME_MS = "time_ms";
    public static final String FACLITY = "_facility";

    // Sending/receiving log messages between vipr nodes uses customized serialization and
    // de-serialization to improve performance.
    // The basic idea is to divide the fields to be serialized into mandatory fields and optional
    // fields. The index array of the optional fields to be present in the log message is to be
    // sent to the stream first, followed by the optional fields values, then followed by
    // mandatory fields.

    // Mandatory fields (not nullable): message, timeMS, severity
    // Optional fields (nullable): nodeId, lineNumber, className, svcName, thread, time, facility
    // Total number of optional fields.
    public static int OPTIONAL_FIELDS_COUNT = 7;

    // A separate byte array is used to store the index(id) of the optional fields
    public final static int INDEX_NODEID = 0;
    public final static int INDEX_LINENO = 1;
    public final static int INDEX_CLASSNAME = 2;
    public final static int INDEX_SVCNAME = 3;
    public final static int INDEX_THREAD = 4;
    public final static int INDEX_TIME = 5;
    public final static int INDEX_FACILITY = 6;
}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.vipr.model.sys.logging;

import java.util.Arrays;
import java.util.List;

/**
 * Enumeration defines the valid severities.
 */
public enum LogSeverity {

    FATAL,
    EMERG,
    ALERT,
    CRIT,
    ERROR("ERROR", "ERR"),
    WARN("WARN", "WARNING"),
    NOTICE,
    INFO,
    DEBUG,
    TRACE,
    NA; //this indicates a get logger level request, shouldn't be used elsewhere

    private final List<String> values;

    LogSeverity(String... values) {
        this.values = Arrays.asList(values);
    }

    public static LogSeverity find(String sevName) {
        for (LogSeverity severity : LogSeverity.values()) {
            if (severity.values.contains(sevName)) {
                return severity;
            } else if (severity.name().equals(sevName)) {
                return severity;
            }
        }
        return null;
    }

    public static int toLevel(String sevName) {
        LogSeverity severity = find(sevName.toUpperCase());
        if (severity != null)
            return severity.ordinal();
        return -1;
    }

    // Corresponds to the value for INFO. Used by a JAX-RS DefaultValue
    // annotation, which requires a constant expression.
    public static final String DEFAULT_VALUE_AS_STR = "7";

    public static final int MAX_LEVEL = 10;
}


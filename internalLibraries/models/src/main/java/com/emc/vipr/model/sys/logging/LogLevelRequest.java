/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.vipr.model.sys.logging;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.vipr.model.sys.logging.LogSeverity;

/**
 * Holds the request parameters for a request made to set/get the log level.
 */
@XmlRootElement
public class LogLevelRequest extends LogRequestBase {

    // log level expiration time in minutes
    private Integer expirInMin;

    // log level scope
    private String scope;

    // Empty constructor
    public LogLevelRequest() {

    }

    /**
     * Constructor.
     *
     * @param nodeIds   The list of Bourne node ids.
     * @param logNames  The list of log file names.
     * @param severity  The minimum desired severity level.
     * @param expirInMin The log level expiration time in minutes.
     * @param scope    The log level scope. 
     */
    public LogLevelRequest(List<String> nodeIds, List<String> logNames,
                          LogSeverity severity, Integer expirInMin, String scope) {
        super(nodeIds, logNames, severity);
        this.expirInMin = expirInMin;
        this.scope = scope;
    }

    /**
     * Copy constructor
     */
    public LogLevelRequest(LogLevelRequest logRequest) {
        super(logRequest);
        expirInMin = logRequest.getExpirInMin();
        scope = logRequest.getScope();
    }

    /**
     * Getter for the log level expiration time.
     *
     * @return The log level expiration time or null if not set.
     */
    @XmlElement(name = "expirInMin")
    public Integer getExpirInMin() {
        return expirInMin;
    }

    /**
     * Setter for the log level expiration time.
     */
    public void setExpirInMin(Integer expirInMin) {
        this.expirInMin = expirInMin;
    }

    /**
     * Getter for the log level scope.
     *
     * @return The log level scope.
     */
    @XmlElement(name = "scope")
    public String getScope() {
        return scope;
    }

    /**
     * Setter for the log level scope.
     */
    public void setScope(String scope) {
        this.scope = scope;
    }
}

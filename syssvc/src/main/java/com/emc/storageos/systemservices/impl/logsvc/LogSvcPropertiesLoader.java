/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.logsvc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Loads the configurable properties for the log service.
 */
@Component
public class LogSvcPropertiesLoader {

    // The delimiter for log file paths specified in the properties file.
    private static final String PATH_DELIM = ";";

    // A semicolon separated list of the log file paths supported by the log
    // service.
    private
    @Value("#{logsvcProperties['logsvc.logFilePaths']}")
    String _logFilePaths;

    @Value("#{logsvcProperties['logsvc.excludedLogFilePaths']}")
    String _excludedLogFilePaths;

    // The timeout in seconds when waiting for a node log collector to complete
    // log collection from a Bourne node in the cluster.
    private
    @Value("#{logsvcProperties['logsvc.nodeLogCollectionTimeout']}")
    int _nodeLogCollectorTimeout;

    // The timeout in seconds when waiting for a file log collector to complete
    // log collection from the log file.
    private
    @Value("#{logsvcProperties['logsvc.fileLogCollectionTimeout']}")
    int _fileLogCollectorTimeout;

    // The timeout in SECONDS when waiting to connect to a node.
    private
    @Value("#{logsvcProperties['logsvc.nodeLogConnectionTimeout']}")
    int _nodeLogConnectionTimeout;

    // The timeout in MILLISECONDS when waiting to collect logs from all nodes.
    private
    @Value("#{logsvcProperties['logsvc.logCollectionTimeout']}")
    long logCollectionTimeout;

    // The expiration time in MINUTES for dynamic log level changes via REST API.
    private
    @Value("#{logsvcProperties['logsvc.logLevelExpiration']}")
    int _logLevelExpiration;

    // This number is multiplied to file size to get zipped size
    private
    @Value("#{logsvcProperties['attachment.zipfactor']}")
    int zipFactor;

    // The maximum attachments size for ConnectEMC events in MB.
    private
    @Value("#{logsvcProperties['attachment.maxSize.MB']}")
    int attachmentMaxSizeMB;

    /**
     * Getter for the supported log file paths.
     *
     * @return A list of the log file paths.
     */
    public List<String> getLogFilePaths() {
        List<String> logFilePathsList = new ArrayList<String>();
        if ((_logFilePaths != null) && (_logFilePaths.trim().length() != 0)) {
            logFilePathsList = Arrays.asList(_logFilePaths.trim().split(PATH_DELIM));
        }

        return logFilePathsList;
    }

    /**
     * Getter for the excluded log file paths.
     *
     * @return A list of the log file paths to be excluded
     */
    public List<String> getExcludedLogFilePaths() {
        List<String> excludedLogFilePathsList = new ArrayList<String>();
        if ((_excludedLogFilePaths != null) && (_excludedLogFilePaths.trim().length() != 0)) {
            excludedLogFilePathsList = Arrays.asList(_excludedLogFilePaths.
                    trim().split(PATH_DELIM));
        }

        return excludedLogFilePathsList;
    }
    
    /**
     * Getter for the node log collector time out.
     *
     * @return The node log collector time out.
     */
    public int getNodeLogCollectorTimeout() {
        return _nodeLogCollectorTimeout;
    }

    /**
     * Getter for the file log collector time out.
     *
     * @return The file log collector time out.
     */
    public int getFileLogCollectorTimeout() {
        return _fileLogCollectorTimeout;
    }

    /**
     * Getter for the node log connection time out.
     *
     * @return The node log connection time out.
     */
    public int getNodeLogConnectionTimeout() {
        return _nodeLogConnectionTimeout;
    }

    public long getLogCollectionTimeout() {
        return logCollectionTimeout;
    }

    /**
     * Getter for the log level expiration.
     *
     * @return The log level expiration.
     */
    public int getLogLevelExpiration() {
        return _logLevelExpiration;
    }

    public int getZipFactor() {
        return zipFactor;
    }

    public int getAttachmentMaxSizeMB() {
        return attachmentMaxSizeMB;
    }
}

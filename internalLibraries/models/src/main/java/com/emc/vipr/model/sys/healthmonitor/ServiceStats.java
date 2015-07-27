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
package com.emc.vipr.model.sys.healthmonitor;

import com.emc.vipr.model.sys.healthmonitor.HealthMonitorConstants;
import com.emc.vipr.model.sys.healthmonitor.ProcModels.ProcessStatus;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents service statistics
 */
@XmlRootElement(name = "service_stats")
public class ServiceStats {

    // Default constructor for JAXB
    public ServiceStats() {
        this.serviceName = HealthMonitorConstants.UNKNOWN;
    }

    public ServiceStats(String serviceName) {
        this.serviceName = serviceName;
    }

    public ServiceStats(String serviceName, String command, int fileDescriptors,
                        ProcessStatus processStatus) {
        this.serviceName = serviceName;
        this.command = command;
        this.fileDescriptors = fileDescriptors;
        this.processStatus = processStatus;
    }

    // proc/[pid]/cmdline
    private String serviceName;
    // proc/[pid]/comm
    private String command;
    // proc/[pid]/stat and  proc/[pid]/statm
    private ProcessStatus processStatus;
    // proc/[pid/fd
    private int fileDescriptors;

    @XmlElement(name = "name")
    public String getServiceName() {
        return serviceName;
    }

    @XmlElement(name = "command")
    public String getCommand() {
        return command;
    }

    @XmlElement(name = "file_descriptors_ctr")
    public int getFileDescriptors() {
        return fileDescriptors;
    }

    @XmlElement(name = "status")
    public ProcessStatus getProcessStatus() {
        return processStatus;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setProcessStatus(ProcessStatus processStatus) {
        this.processStatus = processStatus;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public void setFileDescriptors(int fileDescriptors) {
        this.fileDescriptors = fileDescriptors;
    }
}
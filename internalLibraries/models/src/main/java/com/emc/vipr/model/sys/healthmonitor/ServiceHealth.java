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
package com.emc.vipr.model.sys.healthmonitor;

import com.emc.vipr.model.sys.healthmonitor.HealthMonitorConstants;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents service health
 */
@XmlRootElement(name = "service_health")
public class ServiceHealth {

    // Default constructor for JAXB
    public ServiceHealth() {
        this.serviceName = HealthMonitorConstants.UNKNOWN;
    }

    public ServiceHealth(String serviceName, String status) {
        this.serviceName = serviceName;
        this.status = status;
    }

    private String serviceName;
    private String status;

    @XmlElement(name = "name")
    public String getServiceName() {
        return serviceName;
    }

    @XmlElement(name = "status")
    public String getStatus() {
        return status;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

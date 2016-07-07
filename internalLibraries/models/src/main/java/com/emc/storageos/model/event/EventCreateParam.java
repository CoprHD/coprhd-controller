/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.event;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Request POST parameter for event creation.
 */
@XmlRootElement(name = "event_create")
public class EventCreateParam {

    private String message;
    private String controllerClass;
    private String orchestrationMethod;
    private URI tenant;
    private List<Object> parameters;

    public EventCreateParam() {
    }

    public EventCreateParam(String message) {
        this.message = message;
    }

    @XmlElement(required = true)
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @XmlElement(required = true)
    public String getControllerClass() {
        return controllerClass;
    }

    public void setControllerClass(String controllerClass) {
        this.controllerClass = controllerClass;
    }

    @XmlElement(required = true)
    public String getOrchestrationMethod() {
        return orchestrationMethod;
    }

    public void setOrchestrationMethod(String orchestrationMethod) {
        this.orchestrationMethod = orchestrationMethod;
    }

    @XmlElement(required = true)
    public URI getTenant() {
        return tenant;
    }

    public void setTenant(URI tenant) {
        this.tenant = tenant;
    }

    @XmlElement(required = true)
    public List<Object> getParameters() {
        return parameters;
    }

    public void setParameters(List<Object> parameters) {
        this.parameters = parameters;
    }
}
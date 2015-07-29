/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.impl.eventhandler.beans;

import java.math.BigInteger;

import javax.xml.datatype.XMLGregorianCalendar;

public class AlertEvent {

    private String component = null;

    private String subcomponent = null;

    private int componentID = 0;

    private int subcomponentID = 0;

    private XMLGregorianCalendar firstTime = null;

    private XMLGregorianCalendar lastTime = null;

    private BigInteger count = null;

    private String callHome = null;

    private String errorFilepath = null;

    private String symptomCode = null;

    private String severity = null;

    private String eventData = null;

    private String description = null;

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getSubcomponent() {
        return subcomponent;
    }

    public void setSubcomponent(String subcomponent) {
        this.subcomponent = subcomponent;
    }

    public int getComponentID() {
        return componentID;
    }

    public void setComponentID(int componentID) {
        this.componentID = componentID;
    }

    public int getSubcomponentID() {
        return subcomponentID;
    }

    public void setSubcomponentID(int subcomponentID) {
        this.subcomponentID = subcomponentID;
    }

    public XMLGregorianCalendar getFirstTime() {
        return firstTime;
    }

    public void setFirstTime(XMLGregorianCalendar firstTime) {
        this.firstTime = firstTime;
    }

    public XMLGregorianCalendar getLastTime() {
        return lastTime;
    }

    public void setLastTime(XMLGregorianCalendar lastTime) {
        this.lastTime = lastTime;
    }

    public BigInteger getCount() {
        return count;
    }

    public void setCount(BigInteger count) {
        this.count = count;
    }

    public String getCallHome() {
        return callHome;
    }

    public void setCallHome(String callHome) {
        this.callHome = callHome;
    }

    public String getErrorFilepath() {
        return errorFilepath;
    }

    public void setErrorFilepath(String errorFilepath) {
        this.errorFilepath = errorFilepath;
    }

    public String getSymptomCode() {
        return symptomCode;
    }

    public void setSymptomCode(String symptomCode) {
        this.symptomCode = symptomCode;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getEventData() {
        return eventData;
    }

    public void setEventData(String eventData) {
        this.eventData = eventData;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

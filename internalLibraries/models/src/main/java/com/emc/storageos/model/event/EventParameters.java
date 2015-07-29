/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.event;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "event_create")
public class EventParameters {

    private String userStr;
    private String contact;

    public EventParameters() {
    }

    public EventParameters(String userStr, String contact) {
        this.userStr = userStr;
        this.contact = contact;
    }

    @XmlElement(name = "user_str")
    public String getUserStr() {
        return userStr;
    }

    public void setUserStr(String userStr) {
        this.userStr = userStr;
    }

    @XmlElement(name = "contact")
    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

}

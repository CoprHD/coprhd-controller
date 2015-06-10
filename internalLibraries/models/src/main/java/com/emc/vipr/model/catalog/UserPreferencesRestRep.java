/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;
import java.util.Calendar;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RestLinkRep;

@XmlRootElement(name = "user_preferences")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class UserPreferencesRestRep extends DataObjectRestRep {

    private String username;
    private Boolean notifyByEmail;
    private String email;    
    
    public UserPreferencesRestRep() {
    }

    public UserPreferencesRestRep(String name, URI id, RestLinkRep link, Calendar creationTime, Boolean inactive,
            Set<String> tags) {
        super(name, id, link, creationTime, inactive, tags);
    }

    @XmlElement(name = "notify_by_email")
    public Boolean getNotifyByEmail() {
        return notifyByEmail;
    }

    public void setNotifyByEmail(Boolean notifyByEmail) {
        this.notifyByEmail = notifyByEmail;
    }

    @XmlElement(name = "email")
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @XmlElement(name = "username")
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

}

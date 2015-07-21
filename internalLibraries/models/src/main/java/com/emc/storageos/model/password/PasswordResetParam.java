/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.password;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "password_reset")
public class PasswordResetParam {
    private String username;
    private String password;
    private String encpassword;

    @XmlElement(name = "username")
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }

    @XmlElement(name = "password")
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @XmlElement(name = "encpassword")
    public String getEncPassword() {
        return encpassword;
    }

    public void setEncPassword(String encpassword) {
        this.encpassword = encpassword;
    }
}

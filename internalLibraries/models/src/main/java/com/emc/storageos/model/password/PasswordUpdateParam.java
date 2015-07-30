/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.password;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "password_update")
public class PasswordUpdateParam {

    private String oldPassword;
    private String password;
    private String encpassword;

    @XmlElement(name = "old_password")
    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
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

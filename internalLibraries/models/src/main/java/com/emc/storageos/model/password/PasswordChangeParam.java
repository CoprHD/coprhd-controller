/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.password;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "password_change")
public class PasswordChangeParam {

    private String oldPassword;
    private String password;
    private String username;

    /**
     * Users current valid password to be changed.
     *
     * 
     * @return Returns the current password to be changed.
     */
    @XmlElement(name = "old_password")
    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    /**
     * The new password to be set for the user.
     *
     * 
     * @return Returns the new password to be set.
     */
    @XmlElement(name = "password")
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * User name of the user who's password is
     * being changed.
     *
     * 
     * @return Returns the user's name who's password
     *          is being changed.
     */
    @XmlElement(name = "username")
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}

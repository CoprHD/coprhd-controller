/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.auth;

import com.emc.storageos.model.auth.InvalidLogins;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "block-ips")
public class LoginFailedIPList {

    private int maxLoginAttempts;
    private int lockoutTimeInMinutes;
    private List<InvalidLogins> invalidLoginsList;



    @XmlElement(name = "max_login_attempts")
    public int getMaxLoginAttempts() {
        return  maxLoginAttempts;
    }
    public void setMaxLoginAttempts(int maxLoginAttempts) {
        this.maxLoginAttempts = maxLoginAttempts;
    }

    @XmlElement(name = "lockout_time_in_minutes")
    public int getLockoutTimeInMinutes() {
        return lockoutTimeInMinutes;
    }
    public void setLockoutTimeInMinutes(int lockoutTimeInMinutes) {
        this.lockoutTimeInMinutes = lockoutTimeInMinutes;
    }


    @XmlElementWrapper(name = "ips")
    @XmlElement(name = "ip")
    public List<InvalidLogins>  getInvalidLoginsList() {
        if (invalidLoginsList == null) {
            invalidLoginsList = new ArrayList<InvalidLogins>();
        }

        return invalidLoginsList;
    }
    public void setInvalidLoginsList(List<InvalidLogins> list) {
        this.invalidLoginsList = list;
    }

}

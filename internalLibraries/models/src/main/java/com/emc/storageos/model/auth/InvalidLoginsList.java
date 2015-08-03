/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.model.auth;

import com.emc.storageos.model.auth.InvalidLogins;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "block-ips")
public class InvalidLoginsList {

    private int maxLoginAttempts;
    private int lockoutTimeInMinutes;
    private List<InvalidLogins> invalidLoginsList;



    @XmlElement(name = "max_login_attempts")
    public int getMaxLoginAttemps() {
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

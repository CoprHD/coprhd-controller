/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.password.rules;

import com.emc.storageos.security.password.Password;

public interface Rule {

    /**
     * validate the password per the requirement of the rule.
     * 
     * @param password
     * @return
     */
    public void validate(Password password);
}

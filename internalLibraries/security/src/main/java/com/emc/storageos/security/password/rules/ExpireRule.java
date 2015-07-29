/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security.password.rules;

import com.emc.storageos.db.client.model.PasswordHistory;
import com.emc.storageos.security.password.Password;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;

public class ExpireRule implements Rule {

    private static final Logger _log = LoggerFactory.getLogger(ExpireRule.class);

    private int expireTime = 5;

    /**
     * expire time: in days
     * 
     * @param expireTime
     */
    public ExpireRule(int expireTime) {
        this.expireTime = expireTime;
    }

    /**
     * validate if password expired
     * 
     * @param password
     */
    @Override
    public void validate(Password password) {
        if (expireTime == 0) {
            return;
        }

        String username = password.getUsername();
        if (username == null || username.trim().length() == 0) {
            return;
        }

        PasswordHistory ph = password.getPasswordHistory();
        if (ph == null) {
            return;
        }

        Calendar expireDate = ph.getExpireDate();
        Calendar now = Calendar.getInstance();

        if (expireDate == null) {
            Long lastChangedTime = password.getLatestChangedTime();
            expireDate = Calendar.getInstance();
            expireDate.setTimeInMillis(lastChangedTime);
            expireDate.add(Calendar.DATE, expireTime);
        }

        _log.info("now: " + now + ", " + username + " expire date: " + expireDate);
        if (expireDate.before(now)) {
            _log.info("fail");
            throw BadRequestException.badRequests.passwordExpired(expireTime);
        } else {
            _log.info("pass");
        }

    }
}
